package com.project.service;

import com.project.pojo.Item;
import com.project.service.MatchService.ScoreResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchScoreTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 22, 10, 0, 0);

    @Test
    @DisplayName("样例1：类别/标题/地点/时间全中 -> 100 分")
    void sample1_fullMatch() {
        Item lost = item("LOST", "丢失一把黑色雨伞", "雨具", "图书馆一楼大厅", "黑色", 0);
        Item found = item("FOUND", "黑色雨伞", "雨具", "图书馆一楼大厅", "黑色", 1);

        ScoreResult sr = MatchService.score(lost, found);

        assertEquals(100, sr.score);
        assertTrue(sr.reasons.toString().contains("类别相同"));
        assertTrue(sr.reasons.toString().contains("地点相同"));
        assertTrue(sr.reasons.toString().contains("标题关键词重合度 100%"));
        System.out.println("样例1 分数=" + sr.score + " 理由=" + sr.reasons);
    }

    @Test
    @DisplayName("样例2：时间相差 30 小时 -> 时间项减半，共 95 分")
    void sample2_timeWithin72Hours() {
        Item lost = item("LOST", "白色充电宝丢了", "电子设备", "第三教学楼", "白色", 0);
        Item found = item("FOUND", "白色充电宝", "电子设备", "第三教学楼", "白色", 30);

        ScoreResult sr = MatchService.score(lost, found);

        assertEquals(95, sr.score);
        assertTrue(sr.reasons.toString().contains("时间相差 30.0 小时 +5"));
        System.out.println("样例2 分数=" + sr.score + " 理由=" + sr.reasons);
    }

    @Test
    @DisplayName("样例3：类别/标题/地点都不同 -> 10 分，低于 60 会被过滤")
    void sample3_noRelationship() {
        Item lost = item("LOST", "钥匙串", "钥匙", "第二食堂", null, 0);
        Item found = item("FOUND", "篮球", "体育用品", "体育馆", "棕色", 5);

        ScoreResult sr = MatchService.score(lost, found);

        assertEquals(10, sr.score);
        assertTrue(sr.score < 60);
        System.out.println("样例3 分数=" + sr.score + " 理由=" + sr.reasons);
    }

    @Test
    @DisplayName("样例4：标题部分重合、地点不同 -> 刚好 62 分（≥60 的边界样例）")
    void sample4_boundaryOver60() {
        Item lost = item("LOST", "学生卡 姓名张伟", "证件", "第一食堂", null, 0);
        Item found = item("FOUND", "捡到一张学生卡", "证件", "图书馆总服务台", null, 10);

        ScoreResult sr = MatchService.score(lost, found);

        assertEquals(62, sr.score);
        assertTrue(sr.reasons.toString().contains("标题关键词重合度 40%"));
        System.out.println("样例4 分数=" + sr.score + " 理由=" + sr.reasons);
    }

    @Test
    @DisplayName("样例5：时间相差 100 小时 -> 时间项 0 分，共 90 分")
    void sample5_timeTooFar() {
        Item lost = item("LOST", "黑色雨伞", "雨具", "图书馆一楼大厅", "黑色", 0);
        Item found = item("FOUND", "黑色雨伞", "雨具", "图书馆一楼大厅", "黑色", 100);

        ScoreResult sr = MatchService.score(lost, found);

        assertEquals(90, sr.score);
        assertTrue(sr.reasons.toString().contains("时间相差 100.0 小时 +0"));
        System.out.println("样例5 分数=" + sr.score + " 理由=" + sr.reasons);
    }

    @Test
    @DisplayName("样例6：颜色未知不得分也不重新归一化 -> 仍是 100 分")
    void sample6_colorUnknown() {
        Item lost = item("LOST", "雨伞", "雨具", "体育馆门口", null, 0);
        Item found = item("FOUND", "雨伞", "雨具", "体育馆门口", "黑色", 2);

        ScoreResult sr = MatchService.score(lost, found);

        assertEquals(100, sr.score);
        assertTrue(sr.reasons.toString().contains("颜色未知，不计分"));
        System.out.println("样例6 分数=" + sr.score + " 理由=" + sr.reasons);
    }

    private Item item(String type, String title, String category, String location, String color, int hoursOffset) {
        Item item = new Item();
        item.setType(type);
        item.setTitle(title);
        item.setCategory(category);
        item.setLocation(location);
        item.setColor(color);
        item.setHappenTime(BASE.plusHours(hoursOffset));
        return item;
    }
}
