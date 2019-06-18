package com.nuoxin.virtual.rep.api.service.v3_0.impl;

import com.nuoxin.virtual.rep.api.entity.v3_0.CoverageReportPart;
import com.nuoxin.virtual.rep.api.mybatis.CoverageReportMapper;
import com.nuoxin.virtual.rep.api.service.v3_0.CoverageReportService;
import com.nuoxin.virtual.rep.api.utils.ArithUtil;
import com.nuoxin.virtual.rep.api.utils.ExportExcelUtil;
import com.nuoxin.virtual.rep.api.utils.ExportExcelWrapper;
import com.nuoxin.virtual.rep.api.web.controller.response.v3_0.CoverageOverviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @ClassName CoverageReportServiceImpl
 * @Description 拜访数据汇总 覆盖月报Service实现
 * @Author dangjunhui
 * @Date 2019/6/14 17:04
 * @Version 1.0
 */
@Service
public class CoverageReportServiceImpl implements CoverageReportService {

    private static final String[] MONTH_ARRAY = {"01","02","03","04","05","06","07","08","09","10","11","12"};

    private static final String[] OVERVIEW_DATA_TITLES = {"月份", "已招募医院数", "覆盖医院数", "医院覆盖率", "已招募医生数", "覆盖医生数", "医生覆盖率"};

    @Resource
    private CoverageReportMapper coverageReportMapper;

    @Override
    public Map<String, Object> findOverviewListByProductIdAndTime(Long proId, String startTime, String endTime) {
        // 招募医生部分
        Map<String, Object> map = new HashMap<>(2);
        List<String> yearAndMonth = this.buildYearAndMonth(startTime, endTime);
        map.put("xAxisData", yearAndMonth);
        List<Object> resultList = new ArrayList<>(6);
        List<CoverageReportPart> recruitList = coverageReportMapper.findRecruitList(proId, startTime, endTime);
        if(!CollectionUtils.isEmpty(recruitList)) {
            // 每个时间段的招募医院数量
            Map<String, Set<Long>> recruitListHci = recruitList.stream().collect(Collectors.groupingBy(k -> k.getTimeStr(),
                    Collectors.mapping(k -> k.getHciId(), Collectors.toSet())));
            Map<String, Set<Long>> newRecruitHci = this.buildMap(recruitListHci, yearAndMonth, startTime);
            // 每个时间段的招募医生数量
            Map<String, Set<Long>> recruitListHcp = recruitList.stream().collect(Collectors.groupingBy(k -> k.getTimeStr(),
                    Collectors.mapping(k -> k.getHcpId(), Collectors.toSet())));
            Map<String, Set<Long>> newRecruitHcp = this.buildMap(recruitListHcp, yearAndMonth, startTime);
            // 覆盖医生部分
            List<CoverageReportPart> coverageList = coverageReportMapper.findCoverageList(proId, startTime, endTime);
            if(!CollectionUtils.isEmpty(coverageList)) {
                // 每个时间段的覆盖医院数量
                Map<String, Set<Long>> coverageListHci = coverageList.stream().collect(Collectors.groupingBy(k -> k.getTimeStr(),
                        Collectors.mapping(k -> k.getHciId(), Collectors.toSet())));
                // 每个时间段的招募医生数量
                Map<String, Set<Long>> coverageListHcp = coverageList.stream().collect(Collectors.groupingBy(k -> k.getTimeStr(),
                        Collectors.mapping(k -> k.getHcpId(), Collectors.toSet())));

//                int hciTotalNum = newRecruitHci.get(yearAndMonth.get(yearAndMonth.size()-1)).size();
//                int hcpTotalNum = newRecruitHcp.get(yearAndMonth.get(yearAndMonth.size()-1)).size();

                List<Integer> recruitHciList = new ArrayList<>(13);
                List<Integer> coverageHciList = new ArrayList<>(13);
                List<Double> lineHciList = new ArrayList<>(13);
                List<Integer> recruitHcpList = new ArrayList<>(13);
                List<Integer> coverageHcpList = new ArrayList<>(13);
                List<Double> lineHcpList = new ArrayList<>(13);

                Set<Long> hciSet = new HashSet<>();
                Set<Long> hcpSet = new HashSet<>();
                yearAndMonth.forEach(k -> {
                    Set<Long> rhciSet = newRecruitHci.get(k);
                    int rhciNum = rhciSet != null ? rhciSet.size() : 0;
                    recruitHciList.add(rhciNum);
                    Set<Long> chciSet = coverageListHci.get(k);
                    int hciNum = 0;
                    Double hciPercent = 0.0d;
                    if(chciSet != null) {
                        hciSet.addAll(chciSet);
                        hciSet.retainAll(rhciSet);
                        hciNum = hciSet.size();
                        hciPercent = ArithUtil.mul(ArithUtil.div(hciNum, rhciNum, 4), 100);
                    }
                    coverageHciList.add(hciNum);
                    lineHciList.add(hciPercent);
                    Set<Long> rhcpSet = newRecruitHcp.get(k);
                    int rhcpNum = rhcpSet != null ? rhcpSet.size() : 0;
                    recruitHcpList.add(rhcpNum);
                    Set<Long> chcpSet = coverageListHcp.get(k);
                    int lineNum = 0;
                    Double hcpPercent = 0.0d;
                    if(chcpSet != null) {
                        hcpSet.addAll(chcpSet);
                        hcpSet.retainAll(rhcpSet);
                        lineNum = hcpSet.size();
                        hcpPercent = ArithUtil.mul(ArithUtil.div(lineNum, rhcpNum, 4), 100);
                    }
                    coverageHcpList.add(lineNum);
                    lineHcpList.add(hcpPercent);
                    hciSet.clear();
                    hcpSet.clear();
                });

                resultList.add(recruitHciList);
                resultList.add(coverageHciList);
                resultList.add(lineHciList);
                resultList.add(recruitHcpList);
                resultList.add(coverageHcpList);
                resultList.add(lineHcpList);
            }
            map.put("seriesData", resultList);
        }
        return map;
    }

    @Override
    public void exportOverview(HttpServletResponse response, Long proId, String startTime, String endTime) {
        List<CoverageReportPart> recruitList = coverageReportMapper.findRecruitList(proId, startTime, endTime);
        List<String> yearAndMonth = this.buildYearAndMonth(startTime, endTime);
        if(!CollectionUtils.isEmpty(recruitList)) {
            // 每个时间段的招募医院数量
            Map<String, Set<Long>> recruitListHci = recruitList.stream().collect(Collectors.groupingBy(k -> k.getTimeStr(),
                    Collectors.mapping(k -> k.getHciId(), Collectors.toSet())));
            Map<String, Set<Long>> newRecruitHci = this.buildMap(recruitListHci, yearAndMonth, startTime);
            // 每个时间段的招募医生数量
            Map<String, Set<Long>> recruitListHcp = recruitList.stream().collect(Collectors.groupingBy(k -> k.getTimeStr(),
                    Collectors.mapping(k -> k.getHcpId(), Collectors.toSet())));
            Map<String, Set<Long>> newRecruitHcp = this.buildMap(recruitListHcp, yearAndMonth, startTime);
            // 覆盖医生部分
            List<CoverageReportPart> coverageList = coverageReportMapper.findCoverageList(proId, startTime, endTime);
            if(!CollectionUtils.isEmpty(coverageList)) {
                List<CoverageOverviewResponse> rlist = new ArrayList<>();
                // 每个时间段的覆盖医院数量
                Map<String, Set<Long>> coverageListHci = coverageList.stream().collect(Collectors.groupingBy(k -> k.getTimeStr(),
                        Collectors.mapping(k -> k.getHciId(), Collectors.toSet())));
                // 每个时间段的招募医生数量
                Map<String, Set<Long>> coverageListHcp = coverageList.stream().collect(Collectors.groupingBy(k -> k.getTimeStr(),
                        Collectors.mapping(k -> k.getHcpId(), Collectors.toSet())));

                Set<Long> hciSet = new HashSet<>();
                Set<Long> hcpSet = new HashSet<>();
                yearAndMonth.forEach(k -> {
                    CoverageOverviewResponse res = new CoverageOverviewResponse();
                    res.setTimeStr(k);
                    Set<Long> rhciSet = newRecruitHci.get(k);
                    int rhciNum = rhciSet != null ? rhciSet.size() : 0;
                    res.setRecruitHciNum(rhciNum);
                    Set<Long> chciSet = coverageListHci.get(k);
                    int hciNum = 0;
                    Double hciPercent = 0.0d;
                    if(chciSet != null) {
                        hciSet.addAll(chciSet);
                        hciSet.retainAll(rhciSet);
                        hciNum = hciSet.size();
                        hciPercent = ArithUtil.mul(ArithUtil.div(hciNum, rhciNum, 4), 100);
                    }
                    res.setCoverageHciNum(hciNum);
                    res.setHciPercent(hciPercent + "%");
                    Set<Long> rhcpSet = recruitListHcp.get(k);
                    int rhcpNum = rhcpSet != null ? rhcpSet.size() : 0;
                    res.setRecruitHcpNum(rhcpNum);
                    Set<Long> chcpSet = coverageListHcp.get(k);
                    int lineNum = 0;
                    Double hcpPercent = 0.0d;
                    if(chcpSet != null) {
                        hcpSet.addAll(chcpSet);
                        hcpSet.retainAll(rhcpSet);
                        lineNum = hcpSet.size();
                        hcpPercent = ArithUtil.mul(ArithUtil.div(lineNum, rhcpNum, 4), 100);
                    }
                    res.setCoverageHcpNum(lineNum);
                    res.setHcpPercent(hcpPercent + "%");
                    rlist.add(res);
                    hciSet.clear();
                    hcpSet.clear();
                });
                CoverageOverviewResponse res = new CoverageOverviewResponse();
                res.setTimeStr("总计");
                // 总招募医院数
//                Long recruitHciTotal = recruitList.stream().map(k -> k.getHciId()).distinct().count();
                int hciTotalNum = newRecruitHci.get(yearAndMonth.get(yearAndMonth.size()-1)).size();
                res.setRecruitHciNum(hciTotalNum);
                // 总招募医生数
//                Long recruitHcpTotal = recruitList.stream().map(k -> k.getHcpId()).distinct().count();
                int hcpTotalNum = newRecruitHcp.get(yearAndMonth.get(yearAndMonth.size()-1)).size();
                res.setRecruitHcpNum(hcpTotalNum);
                // 总覆盖医院数
                Long coverageHciTotal = coverageList.stream().map(k -> k.getHciId()).distinct().count();
                res.setCoverageHciNum(coverageHciTotal.intValue());
                // 总覆盖医生数
                Long coverageHcpTotal = coverageList.stream().map(k -> k.getHcpId()).distinct().count();
                res.setCoverageHcpNum(coverageHcpTotal.intValue());
                Double hciPercent = ArithUtil.mul(ArithUtil.div(res.getCoverageHciNum(), res.getRecruitHciNum(), 4), 100);
                res.setHciPercent(hciPercent + "%");
                Double hcpPercent = ArithUtil.mul(ArithUtil.div(res.getCoverageHcpNum(), res.getRecruitHcpNum(), 4), 100);
                res.setHcpPercent(hcpPercent + "%");
                rlist.add(res);
                // 导出逻辑
                ExportExcelWrapper<CoverageOverviewResponse> exportExcelWrapper = new ExportExcelWrapper();
                exportExcelWrapper.exportExcel("月报—招覆盖情况总览—".concat(startTime).concat("-").concat(endTime), "覆盖情况总览表", OVERVIEW_DATA_TITLES,
                        rlist, response, ExportExcelUtil.EXCEl_FILE_2007);
            }
        }
    }

    @Override
    public Map<String, Object> findCallListByProductIdAndTime(Long productId, String startTime, String endTime) {

        return null;
    }

    @Override
    public void exportCall(HttpServletResponse response, Long proId, String startTime, String endTime) {

    }


    /**
     * 根据前端传递的参数构建所有x轴坐标
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return list
     */
    private List<String> buildYearAndMonth(String startTime, String endTime) {
        List<String> list = new ArrayList<>(13);
        if(startTime.equals(endTime)) {
            list.add(startTime);
            return list;
        } else {
            int startY = Integer.parseInt(startTime.substring(0, 4));
            int endY = Integer.parseInt(endTime.substring(0, 4));
            for(int i = startY; i<=endY; i++) {
                for(String s : MONTH_ARRAY) {
                    list.add(i + "-".concat(s));
                }
            }
            return list.subList(list.indexOf(startTime), list.indexOf(endTime) + 1);
        }
    }

    /**
     * 封装累计每个时间段对应的数据
     * @param sourceMap 源数据
     * @param yearAndMonth 月份
     * @param startTime 开始时间
     * @return map
     */
    private Map<String, Set<Long>> buildMap(Map<String, Set<Long>> sourceMap, List<String> yearAndMonth, String startTime) {
        Map<String, Set<Long>> recruitMap = new HashMap<>(yearAndMonth.size());
        List<String> recruitKey = sourceMap.keySet().stream().sorted().collect(Collectors.toList());
        Set<Long> start = new HashSet<>();
        int index = recruitKey.indexOf(startTime);
        for(int i=0; i<= index; i++) {
            start.addAll(sourceMap.get(recruitKey.get(i)));
        }
        recruitMap.put(startTime, start);
        if(index == -1) {
            index = 0;
        }
        for(int i=index; i<recruitKey.size(); i++) {
            recruitMap.put(recruitKey.get(i), sourceMap.get(recruitKey.get(i)));
        }
        for(int i=0; i<yearAndMonth.size()-1; i++) {
            Set<Long> setI = recruitMap.get(yearAndMonth.get(i));
            if(setI == null) {
                setI = Collections.emptySet();
            }
            String j = yearAndMonth.get(i+1);
            Set<Long> setJ = recruitMap.get(j);
            if(setJ == null) {
                setJ = Collections.emptySet();
            }
            setJ.addAll(setI);
            recruitMap.put(j, setJ);
        }
        return recruitMap;
    }

}
