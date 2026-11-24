package com.project.service;

import com.project.auth.LoginUser;
import com.project.auth.UserContext;
import com.project.common.Statuses;
import com.project.mapper.ItemMapper;
import com.project.pojo.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchRecommendationTest {
    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void highestScoringOlderCandidateIsIncludedBeyondFirstTwoHundred() {
        ItemMapper mapper = mock(ItemMapper.class);
        MatchService service = new MatchService(mapper);
        UserContext.set(new LoginUser(1L, "student", Statuses.ROLE_STUDENT));
        Item source = item(2L, LocalDateTime.of(2026, 1, 1, 10, 0));
        source.setType(Statuses.TYPE_LOST);
        when(mapper.selectById(2L)).thenReturn(source);
        List<Item> firstPage = new ArrayList<>();
        for (long id = 202; id > 2; id--) {
            firstPage.add(item(id, source.getHappenTime().plusHours(100)));
        }
        when(mapper.selectMatchCandidates(Statuses.TYPE_FOUND, 0, 200)).thenReturn(firstPage);
        when(mapper.selectMatchCandidates(Statuses.TYPE_FOUND, 200, 200)).thenReturn(List.of(item(1L, source.getHappenTime())));
        var result = service.matches(2L);
        assertEquals(5, result.size());
        assertEquals(1L, result.get(0).getItemId());
        assertEquals(100, result.get(0).getScore());
        assertEquals(3L, result.get(1).getItemId());
    }

    private Item item(Long id, LocalDateTime time) {
        Item item = new Item();
        item.setId(id);
        item.setType(Statuses.TYPE_FOUND);
        item.setTitle("黑色雨伞");
        item.setCategory("雨具");
        item.setLocation("图书馆");
        item.setHappenTime(time);
        item.setStatus(Statuses.ITEM_PUBLIC);
        item.setPublisherId(3L);
        return item;
    }
}
