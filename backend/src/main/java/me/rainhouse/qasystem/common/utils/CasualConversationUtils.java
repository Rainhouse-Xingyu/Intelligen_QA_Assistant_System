package me.rainhouse.qasystem.common.utils;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class CasualConversationUtils {

    private static final String[] BUSINESS_KEYWORDS = {
            "选课", "退课", "补选", "课表", "调课", "重修", "学分", "培养方案",
            "补考", "缓考", "考试", "考场", "准考证", "成绩", "四六级",
            "挂科", "绩点", "学业预警", "帮扶", "留级", "毕业",
            "请假", "销假", "休学", "复学", "转专业", "学籍", "证明",
            "心理", "焦虑", "压力", "失眠", "咨询",
            "怎么", "如何", "怎么办", "什么时候", "啥时候", "在哪", "哪里",
            "入口", "查询", "查看", "条件", "要求", "流程", "申请", "办理", "报名",
            "材料", "时间", "截止", "政策", "规定", "能不能", "可以吗", "是否可以"
    };

    private CasualConversationUtils() {
    }

    public static boolean isCasualOnly(String text) {
        String compact = compact(text);
        if (!StringUtils.hasText(compact)) {
            return false;
        }
        if (containsAny(compact, BUSINESS_KEYWORDS)) {
            return false;
        }
        return isGreeting(compact) || isFarewell(compact) || isThanks(compact) || isAcknowledgement(compact);
    }

    public static String directReply(String text) {
        String compact = compact(text);
        if (isFarewell(compact)) {
            return pick(compact,
                    "好呀，下次见！有问题随时来找我，我一直在线。",
                    "先忙你的，回头有问题再来找我就行。",
                    "收到，那我们下次聊，祝你今天顺顺利利。");
        }
        if (isThanks(compact)) {
            return pick(compact,
                    "不客气呀！能帮上忙就好，有新问题继续丢给我。",
                    "不用客气，能帮到你我也很开心。",
                    "小事小事，还有别的问题也可以继续问我。",
                    "收到你的感谢啦，后面遇到问题随时来问。");
        }
        if (isAcknowledgement(compact)) {
            return pick(compact,
                    "收到收到！还有想问的就继续发我。",
                    "好嘞，我在这儿，有新问题直接说。",
                    "明白，有需要再继续问我就行。");
        }
        return pick(compact,
                "嗨，来啦！今天想查选课、考试、成绩还是学业问题？直接问我就行。",
                "你好呀，我在。可以直接把问题发给我，不用先选分类。",
                "同学你好，有教务、考试、学业或心理方面的问题都可以直接问我。",
                "来了来了，想问什么直接说，我帮你一起看。");
    }

    private static boolean isGreeting(String compact) {
        return compact.matches("(老师|同学|小助手|助手)?(你好|您好|哈喽|hello|hi|hey|嗨|早上好|中午好|下午好|晚上好|早|早安|午安|晚安|在吗|有人吗|在不在|有人不)(呀|啊|哈|哦|喔|呢|嘛|么|啦|哇)*")
                || compact.matches("(你好|您好|哈喽|hello|hi|hey|嗨)(呀|啊|哈|哦|喔|呢|嘛|么|啦|哇)*(在吗|有人吗|在不在)(呀|啊|哈|哦|喔|呢|嘛|么|啦|哇)*");
    }

    private static boolean isFarewell(String compact) {
        return compact.matches("(再见|拜拜|bye|byebye|下次见|回头聊|先这样)(呀|啊|哈|哦|喔|呢|嘛|么|啦|哇)*");
    }

    private static boolean isThanks(String compact) {
        return compact.matches("(谢谢|谢谢你|谢谢啦|感谢|感谢你|多谢|辛苦了|麻烦你了|麻烦啦|太感谢了|帮大忙了|谢了|谢啦|thx|thanks)(呀|啊|哈|哦|喔|呢|嘛|么|啦|哇)*");
    }

    private static boolean isAcknowledgement(String compact) {
        return compact.matches("(好|好的|好滴|好嘞|ok|okay|嗯|嗯嗯|恩|恩恩|行|可以|收到|知道了|明白了|了解了)(呀|啊|哈|哦|喔|呢|嘛|么|啦|哇)*");
    }

    private static String compact(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\p{S}\\s]+", "")
                .trim();
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String pick(String seed, String... replies) {
        if (replies.length == 0) {
            return "";
        }
        int index = Math.floorMod(seed == null ? 0 : seed.hashCode(), replies.length);
        return replies[index];
    }
}
