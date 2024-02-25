package com.qyxa.java.small.tools.scheduler.task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.framework.util.ObjectMapperUtils;
import com.kuaishou.infra.boot.KsSpringApplication;
import com.kuaishou.infra.scheduler.client.Task;
import com.kuaishou.infra.scheduler.client.TaskContext;
import com.kuaishou.kconf.client.Kconf;
import com.kuaishou.kconf.client.annotation.Kconfig;
import com.qyxa.java.small.tools.scheduler.task.base.BaseApplication;
import com.qyxa.java.tools.component.http.HttpUtils;

import kuaishou.common.BizDef;
import lombok.extern.slf4j.Slf4j;

/**
 * @author sunyuxuan <sunyuxuan@kuaishou.com>
 * Created on 2024-02-20
 */
@Slf4j
public class ConvBatchStreamModelInferSwitchAlert extends BaseApplication implements Task {

    @Kconfig("KAIWorks.modelHub.switch_alert_config")
    private Kconf<Map<String, Map<String, Object>>> switchAlertConfig;

    private static final long ONE_DAY = 24 * 60 * 60 * 1000L;

    private static final long ONE_HOUR = 60 * 60 * 1000L;

    private static final long ONE_MINUTE = 60 * 1000L;

    //feature center测试机器人
    //private static String kimRobotUrl = "http://kim-robot.internal/api/robot/send?key=4bbf4c00-7415-4ac1-b68b-490623d3dc0a";

    //infer切换报警机器人
    private static String kimRobotUrl = "http://kim-robot.internal/api/robot/send?key=13c0256a-1f81-4b8c-805f-44e76342b1cb";

    private SimpleDateFormat ftYMDHMS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private SimpleDateFormat ftHMS = new SimpleDateFormat("HH:mm:ss");
    private SimpleDateFormat ftYMD = new SimpleDateFormat("yyyy-MM-dd");

    private String modelHubUrl =
            "https://kml.corp.kuaishou.com/v2/model-hub/api/v1/model/{modelName}/infer-switch-time?scheduleTime={scheduleTime}";

    public static void main(String[] args) {
        KsSpringApplication.justRun(args);
    }

    @Nonnull
    @Override
    public String name() {
        return "ConvBatchStreamModelInferSwitchAlert";
    }

    @Nonnull
    @Override
    public BizDef bizDef() {
        return BizDef.AD_ALGORITHM;
    }

    @Override
    public void execute(@Nonnull TaskContext context) {
        log.info("---------------------------start once--------------------------------");
        StringBuilder kimMarkDownMessage = new StringBuilder();
        long curTime = System.currentTimeMillis() / 1000 * 1000; //精度为s
        String curTimeYMDHMS = ftYMDHMS.format(new Date(curTime));
        log.info("cur time str : {}", curTimeYMDHMS);
        String curTimeYMD = ftYMD.format(new Date(curTime));
        long curTimeAlignToZero = 0;
        try {
            curTimeAlignToZero = ftYMD.parse(curTimeYMD).getTime(); //infer切换查询当天
        } catch (Exception e) {
            log.info("get time fail : {}", e.getMessage());
            return;
        }
        Map<String, Map<String, Object>> report = switchAlertConfig.get();
        for (Entry<String, Map<String, Object>> entry : report.entrySet()) {
            String name = entry.getKey();
            log.info("name : {}", name);

            String modelName = "";
            List<String> notifiedPersons = new ArrayList<>();
            String exceptStartTimeStr = "15:00:00"; //默认期望开始时间
            String exceptEndTimeStr = "20:00:00"; //默认期望结束时间
            for (Entry<String, Object> entry1 : entry.getValue().entrySet()) {
                switch (entry1.getKey()) {
                    case "model_name":
                        modelName = (String) entry1.getValue();
                        break;
                    case "notified_persons":
                        if (entry1.getValue() instanceof ArrayList<?>) {
                            for (Object o : (List<?>) entry1.getValue()) {
                                notifiedPersons.add(String.class.cast(o));
                            }
                        }
                        log.info("notifiedPersons : {}", notifiedPersons);
                        break;
                    case "except_start_time":
                        exceptStartTimeStr = (String) entry1.getValue();
                        break;
                    case "except_end_time":
                        exceptEndTimeStr = (String) entry1.getValue();
                        break;
                    default:
                        log.info("key : {} do not parse !", entry1.getKey());
                        break;
                }
            }

            if (StringUtils.isEmpty(modelName)) {
                log.info("[ERROR]: model name is empty!");
                return;
            }

            long exceptStartTime = 0;
            long exceptEndTime = 0;
            try {
                exceptStartTime = ftYMDHMS.parse(curTimeYMD + " " + exceptStartTimeStr).getTime();
                exceptEndTime = ftYMDHMS.parse(curTimeYMD + " " + exceptEndTimeStr).getTime();
            } catch (Exception e) {
                log.info("Exception : {}", e.getMessage());
            }

            log.info("curTime:{} exceptStartTime:{} exceptEndTime:{}", curTime, exceptStartTime, exceptEndTime);
            boolean startFlag = (curTime >= exceptStartTime) && (curTime - exceptStartTime < 10 * ONE_MINUTE); //每十分钟扫描一次，且只报警一次
            boolean endFlag = (curTime >= exceptEndTime) && (curTime - exceptEndTime < 10 * ONE_MINUTE);
            log.info("start flag:{}, end flag:{}", startFlag, endFlag);
            boolean alertFlag = false;
            long startTime = 0;
            long endTime = 0;
            if (startFlag || endFlag) {
                String requestUrl = modelHubUrl.replace("{modelName}", modelName)
                        .replace("{scheduleTime}", String.valueOf(curTimeAlignToZero));
                log.info("request url: {}", requestUrl);
                Map response = new HashMap<>();
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    response = objectMapper.readValue(HttpUtils.doGet(requestUrl), Map.class);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                log.info("response : {}", response);

                if (startFlag) {
                    if (response.get("inferStartTsMs") != null) {
                        startTime = Long.parseLong(String.valueOf(response.get("inferStartTsMs")));
                        log.info("start time: {} start flag : {}", startTime, startFlag);
                        if (startTime > exceptStartTime) {
                            alertFlag = true;
                        }
                    } else {
                        alertFlag = true;
                    }
                }

                if (endFlag) {
                    if (response.get("inferEndTsMs") != null) {
                        endTime = Long.parseLong(String.valueOf(response.get("inferEndTsMs")));
                        if (endTime > exceptEndTime) {
                            alertFlag = true;
                        }
                        log.info("end time: {} end flag: {}", endTime, endFlag);
                    } else {
                        alertFlag = true;
                    }
                }

            }

            log.info("alert flag : {}", alertFlag);
            if (alertFlag) {
                kimMarkDownMessage.append("### ").append(name).append('\n');
                kimMarkDownMessage.append("model name: ").append(modelName).append('\n');
                if (startFlag) {
                    kimMarkDownMessage.append("期望开始时间: ").append(ftHMS.format(new Date(exceptStartTime))).append('\n');
                    kimMarkDownMessage.append("实际开始时间: ").append("<font color = red>")
                            .append(startTime > 0 ? ftHMS.format(new Date(startTime)) : "未开始")
                            .append("</font>")
                            .append('\n');
                }
                if (endFlag) {
                    kimMarkDownMessage.append("期望结束时间: ").append(ftHMS.format(new Date(exceptEndTime))).append('\n');
                    kimMarkDownMessage.append("实际结束时间: ").append("<font color = red>")
                            .append(endTime > 0 ? ftHMS.format(new Date(endTime)) : "未结束")
                            .append("</font>")
                            .append('\n');
                }
                if (!notifiedPersons.isEmpty()) {
                    for (String notifiedPerson : notifiedPersons) {
                        kimMarkDownMessage.append("<@=username(").append(notifiedPerson).append(")=>");
                    }
                    kimMarkDownMessage.append('\n');
                }
                kimMarkDownMessage.append('\n').append("-------------------------------------").append('\n');
            }
        }

        if (!StringUtils.isEmpty(kimMarkDownMessage)) {
            Map<String, Object> json = new HashMap<String, Object>() {{
                put("msgtype", "markdown");
                Map<String, String> subJson = new HashMap<>();
                subJson.put("content", kimMarkDownMessage.toString());
                put("markdown", subJson);
            }};
            log.info("markdown message: {}", json);
            String response = HttpUtils.doPost(kimRobotUrl, ObjectMapperUtils.toJSON(json));
            log.info("kim url response: {}", response);
        }
        log.info("---------------------------end once--------------------------------");
    }
}
