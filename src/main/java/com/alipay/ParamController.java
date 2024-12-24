package com.alipay;

import cloud.agileframework.common.util.http.RequestMethod;
import cloud.agileframework.spring.util.ParamUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.osgi.context.annotation.OsgiReference;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@HasMigrated
@STCApi
@Controller
@RequestMapping(value = "/api/sca")
public class ParamController extends BaseApiController {

    private static final Logger logger = LoggerFactory.getLogger(ParamController.class);

    @OsgiReference
    private TbaseCacheService tbaseCacheService;

    @OsgiReference
    private CentreArtifactsManager centreArtifactsManager;

    /**
     * NPM进行回调的接口，保证NPM数据及时更新
     *
     * @return
     */
    @RequestMapping(value = "/updateNpm.json", method = RequestMethod.POST)
    @ResponseBody
    public BaseOperationResult updateNpm(@RequestBody NpmUpdateInfo npmUpdateInfoRequest) {
        BaseOperationResult baseOperationResult = new BaseOperationResult();

        Map<String, Object> resultMap = checkAccessKey(npmUpdateInfoRequest.getAccessKey(),
                npmUpdateInfoRequest.getFrom());
        Boolean success = (Boolean) resultMap.get("success");
        if (!Boolean.TRUE.equals(success)) {
            baseOperationResult.setSuccess(false);
            baseOperationResult.setMessage((String) resultMap.get("message")); // 修正为 "message"
            return baseOperationResult;
        }

        if (StringUtil.isBlank(npmUpdateInfoRequest.getPackageName())) {
            baseOperationResult.setSuccess(false);
            baseOperationResult.setMessage((String) resultMap.get("message")); // 修正为 "message"
            return baseOperationResult;
        }

        LoggerUtil.infoPrint(logger,
                "收到NPM订阅通知，其组件名为 " + npmUpdateInfoRequest.getPackageName());

        //处理二级缓存
        String cacheKey = MainStationNPMRegistryUtil.getNPMVersionHistoryKey(
                npmUpdateInfoRequest.getPackageName());
        String cacheValue = null;
        if (tbaseCacheService != null) {
            try {
                cacheValue = tbaseCacheService.getString(cacheKey);
            } catch (Exception e) {
                LoggerUtil.exceptionPrint(logger, "tbase has exception", e);
            }
            //缓存里有此数据，说明SCA是有相关的使用，SCA需要更新
            if (StringUtil.isNotBlank(cacheValue) && !StringUtil.equals(cacheValue, "null")) {
                //获取更新后的结果
                String result = AlipayNPMRegistryUtil.downloadFile(npmUpdateInfoRequest.getPackageName());
                if (StringUtil.isNotBlank(result)) {
                    LoggerUtil.infoPrint(logger,
                            "NPM写入缓存，其组件名为 " + npmUpdateInfoRequest.getPackageName());
                    JSONObject packageJson = JSON.parseObject(result, Feature.OrderedField);
                    //性能考虑 转成简版npmDependencyDefine
                    NPMVersionHistory npmVersionHistory = MainStationNPMRegistryUtil.toNpmVersionHistory(
                            packageJson);

                    //处理NPMVersionHistory 分别二级缓存
                    //写入二级缓存(npmVersionHistory)
                    MainStationNPMRegistryUtil.writeNPMVersionHistoryTBASE(npmVersionHistory,
                            tbaseCacheService, npmUpdateInfoRequest.getPackageName());

                    //处理 该组件的每一个版本和其对应的dependency信息
                    //并且对其进行缓存
                    MainStationNPMRegistryUtil.dealSimpleNPMDependency(packageJson, null,
                            tbaseCacheService);
                }
            }
        }
        ParamUtil.getInParamOfFiles(npmUpdateInfoRequest.getPackage(), npmUpdateInfoRequest.getVersion());
        // 返回结果
        baseOperationResult.setSuccess(true);
        baseOperationResult.setMessage("NPM update successful");
        return baseOperationResult;
    }
}
