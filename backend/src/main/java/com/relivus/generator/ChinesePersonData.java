package com.relivus.generator;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 现代中文姓名与 ASCII 邮箱语料（数据质量整改）。
 *
 * <p>解决演示数据两大脏数据问题：
 * <ol>
 *   <li>姓名：仅使用常见现代单姓与常用名（剔除生僻字、古风复姓），贴合真实业务用户特征；</li>
 *   <li>邮箱：由姓名拼音构成本地部分，保证全 ASCII、RFC 合规（支持中文系统真实收发）。</li>
 * </ol>
 */
final class ChinesePersonData {

    /** 常见现代单姓（与 {@link #SURNAME_PINYIN} 按序对应）。 */
    private static final String[] SURNAMES = {
            "张", "王", "李", "赵", "刘", "陈", "杨", "黄", "周", "吴",
            "徐", "孙", "马", "朱", "胡", "郭", "何", "林", "罗", "郑",
            "梁", "谢", "宋", "唐", "许", "韩", "冯", "邓", "曹", "彭",
            "曾", "肖", "田", "董", "袁", "潘", "于", "蒋", "蔡", "余",
            "杜", "叶", "程", "苏", "魏", "吕", "丁", "任", "沈", "姚"
    };

    private static final String[] SURNAME_PINYIN = {
            "zhang", "wang", "li", "zhao", "liu", "chen", "yang", "huang", "zhou", "wu",
            "xu", "sun", "ma", "zhu", "hu", "guo", "he", "lin", "luo", "zheng",
            "liang", "xie", "song", "tang", "xu", "han", "feng", "deng", "cao", "peng",
            "zeng", "xiao", "tian", "dong", "yuan", "pan", "yu", "jiang", "cai", "yu",
            "du", "ye", "cheng", "su", "wei", "lv", "ding", "ren", "shen", "yao"
    };

    /** 常用现代男名（单/双字，与 {@link #MALE_GIVEN_PINYIN} 按序对应）。 */
    private static final String[] MALE_GIVEN_NAMES = {
            "伟", "强", "磊", "军", "洋", "勇", "杰", "涛", "明", "超",
            "平", "刚", "华", "鹏", "飞", "宇", "波", "彬", "浩", "然",
            "睿", "晨", "凯", "翔", "志强", "浩然", "俊杰", "思远", "文博"
    };

    private static final String[] MALE_GIVEN_PINYIN = {
            "wei", "qiang", "lei", "jun", "yang", "yong", "jie", "tao", "ming", "chao",
            "ping", "gang", "hua", "peng", "fei", "yu", "bo", "bin", "hao", "ran",
            "rui", "chen", "kai", "xiang", "zhiqiang", "haoran", "junjie", "siyuan", "wenbo"
    };

    /** 常用现代女名（单/双字，与 {@link #FEMALE_GIVEN_PINYIN} 按序对应）。 */
    private static final String[] FEMALE_GIVEN_NAMES = {
            "芳", "娜", "敏", "静", "丽", "艳", "娟", "霞", "婷", "璐",
            "洁", "雪", "欣", "楠", "桂", "英", "子涵", "雨欣", "嘉怡", "雅静", "诗涵"
    };

    private static final String[] FEMALE_GIVEN_PINYIN = {
            "fang", "na", "min", "jing", "li", "yan", "juan", "xia", "ting", "lu",
            "jie", "xue", "xin", "nan", "gui", "ying", "zihan", "yuxin", "jiayi", "yajing", "shihan"
    };

    /** 按性别分库的名集合：0=男名库，1=女名库（与拼音数组按序对应）。 */
    private static final String[][] GIVEN_NAMES_BY_GENDER = {MALE_GIVEN_NAMES, FEMALE_GIVEN_NAMES};

    private static final String[][] GIVEN_PINYIN_BY_GENDER = {MALE_GIVEN_PINYIN, FEMALE_GIVEN_PINYIN};

    /** 常用邮箱域名。 */
    private static final String[] DOMAINS = {
            "qq.com", "163.com", "126.com", "gmail.com",
            "outlook.com", "foxmail.com", "hotmail.com", "sina.com"
    };

    private ChinesePersonData() {
    }

    /** 随机现代中文姓名（2~4 字，无生僻字、无复姓），性别随机。 */
    static String randomName() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String[] names = GIVEN_NAMES_BY_GENDER[r.nextInt(GIVEN_NAMES_BY_GENDER.length)];
        return SURNAMES[r.nextInt(SURNAMES.length)] + names[r.nextInt(names.length)];
    }

    /** 按性别取现代中文姓名：男→男名库，女→女名库，其他→随机。 */
    static String randomNameForGender(String gender) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String[] names = "男".equals(gender) ? MALE_GIVEN_NAMES
                : "女".equals(gender) ? FEMALE_GIVEN_NAMES
                : GIVEN_NAMES_BY_GENDER[r.nextInt(GIVEN_NAMES_BY_GENDER.length)];
        return SURNAMES[r.nextInt(SURNAMES.length)] + names[r.nextInt(names.length)];
    }

    /**
     * 判定姓名性别倾向：名字部分命中男名库→男，命中女名库→女，无法识别→null（不强制校正）。
     * 仅用于校验语料库产出的姓名，对用户自定义/外部姓名返回 null 以保持宽容。
     */
    static String genderOfName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String given = name;
        for (String surname : SURNAMES) {
            if (name.startsWith(surname)) {
                given = name.substring(surname.length());
                break;
            }
        }
        for (String g : MALE_GIVEN_NAMES) {
            if (g.equals(given)) {
                return "男";
            }
        }
        for (String g : FEMALE_GIVEN_NAMES) {
            if (g.equals(given)) {
                return "女";
            }
        }
        return null;
    }

    /** 随机 RFC 合规 ASCII 邮箱：拼音姓+名（约 4 成追加 2~4 位数字），全 ASCII、与姓名语义关联。 */
    static String randomEmail() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int genderIdx = r.nextInt(GIVEN_NAMES_BY_GENDER.length);
        String local = SURNAME_PINYIN[r.nextInt(SURNAME_PINYIN.length)]
                + GIVEN_PINYIN_BY_GENDER[genderIdx][r.nextInt(GIVEN_PINYIN_BY_GENDER[genderIdx].length)];
        if (r.nextInt(10) < 4) {
            local += r.nextInt(10, 10_000);
        }
        return local + "@" + DOMAINS[r.nextInt(DOMAINS.length)];
    }
}
