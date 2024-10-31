package com.lgh.tapclick.mybean;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

@Entity(indices = @Index(value = {"appPackage", "appActivity"}, unique = true))
public class Coordinate {
    @PrimaryKey(autoGenerate = true)
    public Integer id;
    public long createTime;
    public String appPackage;
    public String appActivity;
    public int xPosition;
    public int yPosition;
    public int clickDelay;
    public int clickInterval;
    public int clickNumber;
    public String toast;
    public String comment;
    public long lastTriggerTime;
    public int triggerCount;

    public Coordinate() {
        this.appPackage = "";
        this.appActivity = "";
        this.xPosition = 0;
        this.yPosition = 0;
        this.clickDelay = 1000;
        this.clickInterval = 1000;
        this.clickNumber = 1;
        this.toast = "";
        this.comment = "";
        this.lastTriggerTime = 0;
        this.triggerCount = 0;
        this.createTime = System.currentTimeMillis();
    }

    public Coordinate(Coordinate coordinate) {
        this.createTime = coordinate.createTime;
        this.appPackage = coordinate.appPackage;
        this.appActivity = coordinate.appActivity;
        this.xPosition = coordinate.xPosition;
        this.yPosition = coordinate.yPosition;
        this.clickDelay = coordinate.clickDelay;
        this.clickInterval = coordinate.clickInterval;
        this.clickNumber = coordinate.clickNumber;
        this.toast = coordinate.toast;
        this.comment = coordinate.comment;
        this.lastTriggerTime = coordinate.lastTriggerTime;
        this.triggerCount = coordinate.triggerCount;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (!(obj instanceof Coordinate)) return false;
        Coordinate coordinate = (Coordinate) obj;
        return Objects.equals(this.appPackage, coordinate.appPackage)
                && Objects.equals(this.appActivity, coordinate.appActivity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.appPackage, this.appActivity);
    }

    @Override
    public String toString() {
        return "Coordinate{" +
                "id=" + id +
                ", createTime=" + createTime +
                ", appPackage='" + appPackage + '\'' +
                ", appActivity='" + appActivity + '\'' +
                ", xPosition=" + xPosition +
                ", yPosition=" + yPosition +
                ", clickDelay=" + clickDelay +
                ", clickInterval=" + clickInterval +
                ", clickNumber=" + clickNumber +
                ", toast='" + toast + '\'' +
                ", comment='" + comment + '\'' +
                ", lastTriggerTime=" + lastTriggerTime +
                ", triggerCount=" + triggerCount +
                '}';
    }
}
