package com.sss.bibackend.bimq;

import com.rabbitmq.client.Channel;
import com.sss.bibackend.common.ErrorCode;
import com.sss.bibackend.exception.BusinessException;
import com.sss.bibackend.manager.AiManager;
import com.sss.bibackend.model.entity.Chart;
import com.sss.bibackend.model.enums.TaskStatusEnum;
import com.sss.bibackend.service.ChartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 消费者
 */
@Component
@Slf4j
public class BiMessageConsumer {
    @Resource
    private AiManager aiManager;
    @Resource
    private ChartService chartService;
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = BiMqConstant.BI_QUEUE_NAME),
            exchange = @Exchange(name = BiMqConstant.BI_EXCHANGE_NAME,type = ExchangeTypes.DIRECT),
            key = BiMqConstant.BI_ROUTING_KEY
    ),ackMode = "MANUAL")
    private void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("收到的信息是："+message);
        if(message == null){
            channel.basicReject(deliveryTag,false);
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"信息为空");
        }

        //获得chart对象
        Chart chart = chartService.getById(Long.valueOf(message));
        if(message == null){
            channel.basicReject(deliveryTag,false);
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"数据库错误");
        }
        //拼接userInput
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
        userInput.append("分析目标:").append(chart.getGoal()).append("\n");
        userInput.append("图表类型:").append(chart.getChartType()).append("\n");
        userInput.append("原始数据:").append(chart.getChartData()).append("\n");

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
        try {
            //deliverTag唯一id，false否批量确认
            channel.basicAck(deliveryTag,false);
        } catch (IOException e) {
            log.error("消息确认失败");
            throw new RuntimeException(e);
        }
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
}
