package com.sss.bibackend.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sss.bibackend.common.ErrorCode;
import com.sss.bibackend.common.PageRequest;

import com.sss.bibackend.constant.CommonConstant;
import com.sss.bibackend.exception.BusinessException;
import com.sss.bibackend.mapper.ChartMapper;
import com.sss.bibackend.model.dto.chart.ChartQueryRequest;

import com.sss.bibackend.model.entity.Chart;
import com.sss.bibackend.model.entity.User;
import com.sss.bibackend.service.ChartService;
import com.sss.bibackend.service.UserService;
import com.sss.bibackend.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图表服务实现
 */
@Service
@Slf4j
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart> implements ChartService {

    @Resource
    private UserService userService;


    @Override
    public void validChart(Chart chart, boolean add) {

    }

    @Override
    public Chart getChartVO(Chart chart, HttpServletRequest request) {
        return null;
    }

    @Override
    public Page<Chart> getChartVOPage(Page<Chart> chartPage, HttpServletRequest request) {
        return null;
    }

}




