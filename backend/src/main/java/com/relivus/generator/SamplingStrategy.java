package com.relivus.generator;

/**
 * 父表主键采样策略。
 */
public enum SamplingStrategy {
    /** 均匀采样：随机偏移取行。 */
    UNIFORM,
    /** 倾斜采样：偏向序号靠前的键（近似 Zipf）。 */
    ZIPF
}