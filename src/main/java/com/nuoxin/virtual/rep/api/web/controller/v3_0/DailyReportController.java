package com.nuoxin.virtual.rep.api.web.controller.v3_0;

import com.nuoxin.virtual.rep.api.common.bean.DefaultResponseBean;
import com.nuoxin.virtual.rep.api.common.bean.PageResponseBean;
import com.nuoxin.virtual.rep.api.entity.DrugUser;
import com.nuoxin.virtual.rep.api.entity.v3_0.params.ContentSharingParams;
import com.nuoxin.virtual.rep.api.entity.v3_0.request.ContentSharingRequest;
import com.nuoxin.virtual.rep.api.service.v3_0.DailyReportService;
import com.nuoxin.virtual.rep.api.service.v3_0.MyDoctorService;
import com.nuoxin.virtual.rep.api.web.controller.request.v3_0.DailyReportRequest;
import com.nuoxin.virtual.rep.api.web.controller.request.v3_0.MyDoctorRequest;
import com.nuoxin.virtual.rep.api.web.controller.response.v3_0.DailyReportResponse;
import com.nuoxin.virtual.rep.api.web.controller.response.v3_0.MyDoctorResponse;
import com.nuoxin.virtual.rep.api.web.controller.v2_5.NewBaseController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

/**
 * 我的客户相关接口
 * @author tiancun
 * @date 2019-04-28
 */
@RestController
@Api(value = "V3.0.1日报接口")
@RequestMapping(value = "/daily/report")
public class DailyReportController extends NewBaseController {

    @Resource
    private DailyReportService dailyReportService;

    @ApiOperation(value = "展示数据")
    @RequestMapping(value = "/detail", method = { RequestMethod.POST})
    public DefaultResponseBean<DailyReportResponse> getDailyReport(@RequestBody DailyReportRequest request){
        DailyReportResponse dailyReport = dailyReportService.getDailyReport(request);
        DefaultResponseBean<DailyReportResponse> responseBean = new DefaultResponseBean<>();
        responseBean.setData(dailyReport);
        return responseBean;
    }


}
