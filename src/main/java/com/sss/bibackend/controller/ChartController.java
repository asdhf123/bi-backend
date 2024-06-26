package com.sss.bibackend.controller;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;


import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.auditing.DocumentAuditingRequest;
import com.qcloud.cos.model.ciModel.auditing.DocumentAuditingResponse;
import com.qcloud.cos.region.Region;
import com.sss.bibackend.annotation.AuthCheck;
import com.sss.bibackend.bimq.BiMessageProducer;
import com.sss.bibackend.common.BaseResponse;
import com.sss.bibackend.common.DeleteRequest;
import com.sss.bibackend.common.ErrorCode;
import com.sss.bibackend.common.ResultUtils;
import com.sss.bibackend.constant.CommonConstant;
import com.sss.bibackend.constant.UserConstant;
import com.sss.bibackend.exception.BusinessException;
import com.sss.bibackend.manager.AiManager;
import com.sss.bibackend.manager.RedisLimiterManager;
import com.sss.bibackend.model.dto.chart.ChartAddRequest;
import com.sss.bibackend.model.dto.chart.ChartEditRequest;
import com.sss.bibackend.model.dto.chart.ChartQueryRequest;
import com.sss.bibackend.model.dto.chart.ChartUpdateRequest;
import com.sss.bibackend.model.dto.chart.GetChartByAiDTO;
import com.sss.bibackend.model.entity.Chart;
import com.sss.bibackend.model.entity.User;
import com.sss.bibackend.model.enums.TaskStatusEnum;
import com.sss.bibackend.model.vo.chart.BiResponse;
import com.sss.bibackend.service.ChartService;
import com.sss.bibackend.service.UserService;
import com.sss.bibackend.utils.ExcelUtils;
import com.sss.bibackend.utils.SqlUtils;
import jodd.io.upload.FileUpload;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ehcache.impl.internal.resilience.RobustResilienceStrategy;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 图表接口
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {
    @Resource
    private AiManager aiManager;
    @Resource
    private ChartService chartService;
    @Resource
    private UserService userService;
    @Resource
    private RedisLimiterManager redisLimiterManager;
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;
    @Resource
    private BiMessageProducer biMessageProducer;

    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);


        chartService.validChart(chart, true);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());

        boolean result = chartService.save(chart);
        if(!result){
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        if(oldChart == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        // 参数校验
        chartService.validChart(chart, false);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        if(oldChart == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（仅管理员）
     *
     * @param chartQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                this.getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        if(size>20){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                this.getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartService.getChartVOPage(chartPage, request));
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        if(loginUser == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"请先登录");
        }
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        if(size>20){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                this.getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion


    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        // 参数校验
        chartService.validChart(chart, false);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        if(oldChart == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        // 拼接查询条件
        queryWrapper.eq(id!=null && id>0,"id",id);
        queryWrapper.like(StringUtils.isNotBlank(name),"name",name);
        queryWrapper.eq(StringUtils.isNotBlank(goal),"goal",goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType),"chartType",chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId),"userId",userId);
        queryWrapper.eq("isDelete",false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),sortOrder.equals(CommonConstant.SORT_ORDER_ASC),sortField);
        return queryWrapper;
    }
    /**
     * AI生成图表：同步
     *
     * @param multipartFile
     * @param getChartByAiDTO
     * @param request
     * @return
     */
    @PostMapping("/getchart")
    public BaseResponse<BiResponse> getChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GetChartByAiDTO getChartByAiDTO, HttpServletRequest request) {
        //获取用户登陆信息
        User user = userService.getLoginUser(request);
        if(user == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"请先登录");
        }
        //验空
        String name = getChartByAiDTO.getName();
        String chartType = getChartByAiDTO.getChartType();
        String goal = getChartByAiDTO.getGoal();
        if(StringUtils.isBlank(name)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"名称不能为空");
        }
        if(StringUtils.isBlank(goal)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"目标不能为空！");
        }
        //校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        //校验文件大小
        final long TEN_MB = 10*1024*1024L;
        if(size>TEN_MB){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件超过10M");
        }
        //校验文件后缀
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> whiteFileSuffixList = Arrays.asList("xlsx","xls");
        if(!whiteFileSuffixList.contains(suffix)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件后缀不合规");
        }

        //限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("getChartByAi_"+user.getId());

        String csvData;
        try {
            csvData = ExcelUtils.excelToCsv(multipartFile);
            System.out.println(csvData);
        } catch (IOException e) {
            log.error("文件处理失败",e);
            throw new RuntimeException(e);
        }
        //用户输入
        StringBuffer userInput = new StringBuffer();
        userInput.append("你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
                "分析目标：\n" +
                "{数据分析的需求或者目标}\n" +
                "图表类型：\n"+
                "{指定生成前端代码的图表类型,如折线图，柱状图等}"+
                "原始数据：\n" +
                "{csv格式的原始数据，用,作为分隔符}\n" +
                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）:\n" +
                "【【【【【\n" +
                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
                "【【【【【\n" +
                "{明确的数据分析结论、越详细越好，不要生成多余的注释,}").append("\n");

        userInput.append("分析目标:").append(goal).append("\n");
        userInput.append("请使用").append(chartType).append("\n");
        userInput.append("原始数据:").append(csvData).append("\n");
        userInput.append("最终格式是：【【【【【前端代码【【【【【分析结论\n");

        String result = aiManager.doChat(userInput.toString());
        String[] splits = result.split("【【【【【");
        if(splits.length<3){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI生成错误");
        }
        String genChart = splits[1];
        String genResult = splits[2];
        //将图表存入数据库
        Chart chart = new Chart();
        chart.setGoal(goal);
        chart.setName(name);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setUserId(user.getId());
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setStatus("succeed");
        boolean saveReslt = chartService.save(chart);
        if(!saveReslt){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"图标保存失败");
        }

        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }

    /**
     * AI生成图表：异步
     *
     * @param multipartFile
     * @param getChartByAiDTO
     * @param request
     * @return
     */
    @PostMapping("/getchart/async")
    public BaseResponse<BiResponse> getChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                             GetChartByAiDTO getChartByAiDTO, HttpServletRequest request) {
        //获取用户登陆信息
        User user = userService.getLoginUser(request);
        if(user == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"请先登录");
        }
        //验空
        String name = getChartByAiDTO.getName();
        String chartType = getChartByAiDTO.getChartType();
        String goal = getChartByAiDTO.getGoal();
        if(StringUtils.isBlank(name)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"名称不能为空");
        }
        if(StringUtils.isBlank(goal)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"目标不能为空！");
        }
        //校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        //校验文件大小
        final long TEN_MB = 10*1024*1024L;
        if(size>TEN_MB){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件超过10M");
        }
        //校验文件后缀
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> whiteFileSuffixList = Arrays.asList("xlsx","xls");
        if(!whiteFileSuffixList.contains(suffix)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件后缀不合规");
        }

        //限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("getChartByAi_"+user.getId());

        String csvData;
        try {
            csvData = ExcelUtils.excelToCsv(multipartFile);
            System.out.println(csvData);
        } catch (IOException e) {
            log.error("文件处理失败",e);
            throw new RuntimeException(e);
        }

        //用户输入
        StringBuffer userInput = new StringBuffer();
        userInput.append("你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
                "分析目标：\n" +
                "{数据分析的需求或者目标}\n" +
                "图表类型：\n"+
                "{指定图表类型,如折线图，柱状图等}"+
                "原始数据：\n" +
                "{csv格式的原始数据，用,作为分隔符}\n" +
                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）:\n" +
                "【【【【【\n" +
                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
                "【【【【【\n" +
                "{明确的数据分析结论、越详细越好，不要生成多余的注释}").append("\n");
        userInput.append("输出内容格式示例：\n"+
                "【【【【【\n" +
                "{\n" +
                "  \"title\": {\n" +
                "    \"text\": \"网站访问量变化趋势\"\n" +
                "  },\n" +
                "  \"tooltip\": {\n" +
                "    \"trigger\": \"axis\"\n" +
                "  },\n" +
                "  \"legend\": {\n" +
                "    \"data\": [\"人数\"]\n" +
                "  },\n" +
                "  \"xAxis\": {\n" +
                "    \"data\": [\"1\", \"2\", \"3\", \"4\"]\n" +
                "  },\n" +
                "  \"yAxis\": {},\n" +
                "  \"series\": [\n" +
                "    {\n" +
                "      \"name\": \"人数\",\n" +
                "      \"type\": \"{指定图表类型}\",\n" +
                "      \"data\": [10, 20, 30, 50]\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "【【【【【\n" +
                "根据这些数据，我们可以得出以下结论......\n");
        userInput.append("分析目标:").append(goal).append("\n");
        userInput.append("图表类型:").append(chartType).append("\n");
        userInput.append("原始数据:").append(csvData).append("\n");

        //将图表存入数据库
        Chart chart = new Chart();
        chart.setGoal(goal);
        chart.setName(name);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setUserId(user.getId());
        //设置状态为排队中
        chart.setStatus(TaskStatusEnum.WAIT.getValue());

        boolean saveReslt = chartService.save(chart);
        if(!saveReslt){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"图表保存失败");
        }
        //todo: 处理任务队列满了后，抛异常情况，因为提交任务报错了，前端会返回异常
        CompletableFuture.runAsync(()->{
            //更新任务状态为执行中并保存
            Chart updateChart = new Chart();
            updateChart.setStatus(TaskStatusEnum.RUNNING.getValue());
            updateChart.setId(chart.getId());
            boolean updateResult = chartService.updateById(updateChart);
            if(!updateResult){
                handleChartUpdateError(chart.getId(),"更新图表执行中状态失败");
            }
            //调用AI
            String result = aiManager.doChat(userInput.toString());
            String[] splits = result.split("【【【【【");
            if(splits.length<3){
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI生成错误");
            }
            String genChart = splits[1];
            String genResult = splits[2];
            //更新任务状态为成功并保存
            Chart resultChart = new Chart();
            resultChart.setStatus(TaskStatusEnum.SUCCEED.getValue());
            resultChart.setGenChart(genChart);
            resultChart.setGenResult(genResult);
            resultChart.setId(chart.getId());
            boolean finalResult = chartService.updateById(resultChart);
            if(!finalResult){
                handleChartUpdateError(chart.getId(),"更新图表成功状态失败");
            }
        },threadPoolExecutor);

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }

    /**
     * AI生成图表：异步、使用MQ
     *
     * @param multipartFile
     * @param getChartByAiDTO
     * @param request
     * @return
     */
    @PostMapping("/getchart/asyncmq")
    public BaseResponse<BiResponse> getChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                      GetChartByAiDTO getChartByAiDTO, HttpServletRequest request) {
        //获取用户登陆信息
        User user = userService.getLoginUser(request);
        if(user == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"请先登录");
        }
        //验空
        String name = getChartByAiDTO.getName();
        String chartType = getChartByAiDTO.getChartType();
        String goal = getChartByAiDTO.getGoal();
        if(StringUtils.isBlank(name)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"名称不能为空");
        }
        if(StringUtils.isBlank(goal)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"目标不能为空！");
        }
        //校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        //校验文件大小
        final long TEN_MB = 10*1024*1024L;
        if(size>TEN_MB){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件超过10M");
        }
        //校验文件后缀
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> whiteFileSuffixList = Arrays.asList("xlsx","xls");
        if(!whiteFileSuffixList.contains(suffix)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件后缀不合规");
        }
        //接入数据万象内容审核
        cosCheck(multipartFile);

        //限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("getChartByAi_"+user.getId());

        String csvData;
        try {
            csvData = ExcelUtils.excelToCsv(multipartFile);
            System.out.println(csvData);
        } catch (IOException e) {
            log.error("文件处理失败",e);
            throw new RuntimeException(e);
        }

        //将图表存入数据库
        Chart chart = new Chart();
        chart.setGoal(goal);
        chart.setName(name);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setUserId(user.getId());
        //设置状态为排队中
        chart.setStatus(TaskStatusEnum.WAIT.getValue());

        boolean saveReslt = chartService.save(chart);
        if(!saveReslt){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"图表保存失败");
        }
        //将id发送给消息队列
        biMessageProducer.sendMessage(chart.getId().toString());

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }

    private void handleChartUpdateError(long chartId,String execMessage){
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus(TaskStatusEnum.FAILED.getValue());
        updateChartResult.setExecMessage(execMessage);
        boolean result = chartService.updateById(updateChartResult);
        if(!result){
            log.info("更新图表失败状态失败"+chartId+","+execMessage);
        }
    }

    /**
     * 数据万象文本审核
     * @param multipartFile
     */
    public void cosCheck(MultipartFile multipartFile){
        // 初始化用户身份信息(secretId, secretKey)
        COSCredentials cred = new BasicCOSCredentials("","");
        // 设置bucket的区域, COS地域的简称请参照 https://www.qcloud.com/document/product/436/6224
        ClientConfig clientConfig = new ClientConfig(new Region("ap-beijing"));
        // 生成cos客户端
        COSClient cosClient = new COSClient(cred, clientConfig);
        //将文件转为inputstream
        InputStream inputStream;
        try {
            inputStream = multipartFile.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException("文件转换出错："+e);
        }
        ObjectMetadata objectMetadata = new ObjectMetadata();
// 指定文件将要存放的存储桶
        String bucketName = "bi-1324629091";
// 指定文件上传到 COS 上的路径，即对象键。例如对象键为 folder/picture.jpg，则表示将文件 picture.jpg 上传到 folder 路径下
        String key = "folder/"+multipartFile.getOriginalFilename();
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, inputStream,objectMetadata);
        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
        System.out.println(putObjectResult);

        //1.创建任务请求对象
        DocumentAuditingRequest cosRequest = new DocumentAuditingRequest();
        //2.添加请求参数 参数详情请见 API 接口文档
        cosRequest.setBucketName(bucketName);
        //2.1.1设置对象地址
        cosRequest.getInput().setObject("folder/"+multipartFile.getOriginalFilename());
        System.out.println(multipartFile.getName()+"-"+multipartFile.getOriginalFilename());
        //2.1.2或直接设置文档地址
        //2.2设置审核类型参数
        cosRequest.getConf().setDetectType("all");
        //3.调用接口,获取任务响应对象
        DocumentAuditingResponse response = cosClient.createAuditingDocumentJobs(cosRequest);
        System.out.println(response.getJobsDetail().getState());
        cosRequest.setJobId(response.getJobsDetail().getJobId());

        // 循环检查异步任务的状态，直到状态不为 "submitted" 为止
        while (response.getJobsDetail().getState().equals("Submitted") || response.getJobsDetail().getState().equals("Auditing")) {
            try {
                // 每次循环等待一段时间后重新获取异步任务的状态
                Thread.sleep(500); // 等待0.5秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 重新获取异步任务的状态
            response = cosClient.describeAuditingDocumentJob(cosRequest);
        }

        //2.添加请求参数 参数详情请见 API 接口文档
        cosRequest.setBucketName(bucketName);

        //3.调用接口,获取任务响应对象
        DocumentAuditingResponse result = cosClient.describeAuditingDocumentJob(cosRequest);
        if(!result.getJobsDetail().getState().equals("Success")){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"文件内容违规!");
        }
    }


}
