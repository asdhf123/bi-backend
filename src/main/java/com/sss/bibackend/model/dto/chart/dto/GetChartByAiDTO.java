package com.sss.bibackend.model.dto.chart.dto;

import lombok.Data;

import java.awt.*;
import java.io.Serializable;

@Data
public class GetChartByAiDTO implements Serializable {
    private String name;
    private String goal;
    private String charData;
    private String charType;
    private static final long serialVersionUID = 1L;
}
