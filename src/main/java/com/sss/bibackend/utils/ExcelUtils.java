package com.sss.bibackend.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import io.swagger.models.auth.In;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * excel处理工具类
 */
public class ExcelUtils {
    public static String excelToCsv(MultipartFile multipartFile) throws IOException {
        List<Map<Integer,String>> list = EasyExcel.read(multipartFile.getInputStream())
                .excelType(ExcelTypeEnum.XLSX)
                .sheet()
                .headRowNumber(0)
                .doReadSync();
        //判空
        if(CollectionUtils.isEmpty(list)){
            return "";
        }
        //excel->csv
        StringBuffer stringBuffer = new StringBuffer();
        LinkedHashMap<Integer, String> headerMap = (LinkedHashMap<Integer, String>) list.get(0);
        //过滤空值
        List<String> headList = headerMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
        stringBuffer.append(StringUtils.join(headList,",")).append("\n");
        //读取数据
        for(int i = 1;i<list.size();i++){
            LinkedHashMap<Integer, String> dataMap = (LinkedHashMap<Integer, String>) list.get(i);
            List<String> dataList = dataMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
            stringBuffer.append(StringUtils.join(dataList,",")).append("\n");
        }
        return stringBuffer.toString();
    }
}
