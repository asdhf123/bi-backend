package com.sss.bibackend.model.dto.chart;

import lombok.Data;

import java.awt.*;
import java.io.Serializable;

@Data
public class GetChartByAiDTO implements Serializable {
    private String name;
    private String goal;
    private String chartData;
    private String chartType;
    private static final long serialVersionUID = 1L;
}
