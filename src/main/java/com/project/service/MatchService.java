package com.project.service;

import com.project.auth.LoginUser;
import com.project.auth.UserContext;
import com.project.common.BizException;
import com.project.common.Statuses;
import com.project.mapper.ItemMapper;
import com.project.pojo.Item;
import com.project.vo.MatchVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MatchService {

    private static final int MIN_SCORE = 60;
    private static final int MAX_RESULT = 5;
    private static final int CANDIDATE_LIMIT = 200;

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fa5]+");
    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");

    private final ItemMapper itemMapper;

    public MatchService(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    public List<MatchVO> matches(Long itemId) {
        LoginUser me = UserContext.require();
        Item source = itemMapper.selectById(itemId);
        if (source == null) {
            throw BizException.of404("信息不存在");
        }
        boolean owner = me.getId().equals(source.getPublisherId());
        if (!Statuses.ITEM_PUBLIC.equals(source.getStatus()) && !owner && !Statuses.isAdmin(me.getRole())) {
            throw BizException.of404("信息不存在或未公开");
        }

        String oppositeType = Statuses.isLost(source.getType()) ? Statuses.TYPE_FOUND : Statuses.TYPE_LOST;
        List<Item> candidates = itemMapper.selectMatchCandidates(oppositeType, CANDIDATE_LIMIT);

        List<MatchVO> result = new ArrayList<>();
        for (Item candidate : candidates) {
            ScoreResult sr = score(source, candidate);
            if (sr.score >= MIN_SCORE) {
                result.add(toVO(candidate, sr));
            }
        }

        result.sort(Comparator.comparingInt(MatchVO::getScore).reversed()
                .thenComparing(MatchVO::getItemId));
        return result.size() > MAX_RESULT ? result.subList(0, MAX_RESULT) : result;
    }

    private MatchVO toVO(Item item, ScoreResult sr) {
        MatchVO vo = new MatchVO();
        vo.setItemId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setType(item.getType());
        vo.setCategory(item.getCategory());
        vo.setLocation(item.getLocation());
        vo.setHappenTime(item.getHappenTime());
        vo.setColor(item.getColor());
        vo.setScore(sr.score);
        vo.setReasons(sr.reasons);
        return vo;
    }

    public static ScoreResult score(Item a, Item b) {
        int total = 0;
        List<String> reasons = new ArrayList<>();

        if (same(a.getCategory(), b.getCategory())) {
            total += 40;
            reasons.add("类别相同（" + a.getCategory() + "）+40");
        } else {
            reasons.add("类别不同（" + nvl(a.getCategory()) + " / " + nvl(b.getCategory()) + "）+0");
        }

        double similarity = titleSimilarity(a.getTitle(), b.getTitle());
        int titleScore = (int) Math.round(30 * similarity);
        total += titleScore;
        reasons.add("标题关键词重合度 " + Math.round(similarity * 100) + "% +" + titleScore);

        if (same(a.getLocation(), b.getLocation())) {
            total += 20;
            reasons.add("地点相同（" + a.getLocation() + "）+20");
        } else {
            reasons.add("地点不同（" + nvl(a.getLocation()) + " / " + nvl(b.getLocation()) + "）+0");
        }

        double hours = Math.abs(Duration.between(a.getHappenTime(), b.getHappenTime()).toMinutes()) / 60.0;
        int timeScore;
        if (hours <= 24) {
            timeScore = 10;
        } else if (hours <= 72) {
            timeScore = 5;
        } else {
            timeScore = 0;
        }
        total += timeScore;
        reasons.add("时间相差 " + String.format(Locale.ROOT, "%.1f", hours) + " 小时 +" + timeScore);

        if (isBlank(a.getColor()) || isBlank(b.getColor())) {
            reasons.add("颜色未知，不计分");
        } else if (a.getColor().trim().equalsIgnoreCase(b.getColor().trim())) {
            reasons.add("颜色相同（" + a.getColor() + "），不计分");
        } else {
            reasons.add("颜色不同（" + a.getColor() + " / " + b.getColor() + "），不计分");
        }

        return new ScoreResult(total, reasons);
    }

    public static double titleSimilarity(String t1, String t2) {
        Set<String> a = tokens(t1);
        Set<String> b = tokens(t2);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int intersect = 0;
        for (String token : a) {
            if (b.contains(token)) {
                intersect++;
            }
        }
        return (double) intersect / Math.min(a.size(), b.size());
    }

    static Set<String> tokens(String title) {
        Set<String> tokens = new LinkedHashSet<>();
        if (isBlank(title)) {
            return tokens;
        }
        Matcher cjk = CJK.matcher(title);
        while (cjk.find()) {
            String run = cjk.group();
            if (run.length() == 1) {
                tokens.add(run);
                continue;
            }
            for (int i = 0; i + 1 < run.length(); i++) {
                tokens.add(run.substring(i, i + 2));
            }
        }
        Matcher word = WORD.matcher(title.toLowerCase(Locale.ROOT));
        while (word.find()) {
            tokens.add(word.group());
        }
        return tokens;
    }

    private static boolean same(String a, String b) {
        return !isBlank(a) && !isBlank(b) && a.trim().equalsIgnoreCase(b.trim());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nvl(String s) {
        return s == null ? "空" : s;
    }

    public static class ScoreResult {
        public final int score;
        public final List<String> reasons;

        public ScoreResult(int score, List<String> reasons) {
            this.score = score;
            this.reasons = reasons;
        }
    }
}
