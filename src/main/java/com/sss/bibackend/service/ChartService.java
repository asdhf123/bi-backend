package com.sss.bibackend.service;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sss.bibackend.model.entity.Chart;

import javax.servlet.http.HttpServletRequest;

/**
* @author lenovo
* @description 针对表【chat(图表信息表)】的数据库操作Service
* @createDate 2024-03-18 17:29:44
*/
public interface ChartService extends IService<Chart> {

    /**
     * 校验
     *
     * @param chart
     * @param add
     */
    void validChart(Chart chart, boolean add);





    /**
     * 获取帖子封装
     *
     * @param chart
     * @param request
     * @return
     */
    Chart getChartVO(Chart chart, HttpServletRequest request);

    /**
     * 分页获取帖子封装
     *
     * @param chartPage
     * @param request
     * @return
     */
    Page<Chart> getChartVOPage(Page<Chart> chartPage, HttpServletRequest request);

}
