package com.campusarrive.integration.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 事件业务负载类型 — 报到成功负载。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.4 节、第 5.3 节事件链一。
 * 报到服务发布 student.checkin.success 时携带的业务数据。</p>
 */
public class CheckinSuccessPayload {

    @JsonProperty("studentId")
    private String studentId;

    @JsonProperty("idCard")
    private String idCard;

    @JsonProperty("name")
    private String name;

    @JsonProperty("checkinTime")
    private String checkinTime;

    @JsonProperty("checkinPoint")
    private String checkinPoint;

    public CheckinSuccessPayload() {
    }

    public CheckinSuccessPayload(String studentId, String idCard, String name,
                                  String checkinTime, String checkinPoint) {
        this.studentId = studentId;
        this.idCard = idCard;
        this.name = name;
        this.checkinTime = checkinTime;
        this.checkinPoint = checkinPoint;
    }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCheckinTime() { return checkinTime; }
    public void setCheckinTime(String checkinTime) { this.checkinTime = checkinTime; }
    public String getCheckinPoint() { return checkinPoint; }
    public void setCheckinPoint(String checkinPoint) { this.checkinPoint = checkinPoint; }
}
