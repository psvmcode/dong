package com.dong.lab.classic.dto;

/**
 * 排行榜单项响应。
 */
public class RankItemResponse {

    /**
     * 成员标识。
     */
    private String member;

    /**
     * 分数。
     */
    private double score;

    /**
     * 名次。
     */
    private long rank;

    /**
     * 创建排行榜单项响应。
     *
     * @param member 成员标识
     * @param score  分数
     * @param rank   名次
     * @return 排行榜单项响应
     */
    public static RankItemResponse of(String member, double score, long rank) {
        RankItemResponse response = new RankItemResponse();
        response.setMember(member);
        response.setScore(score);
        response.setRank(rank);
        return response;
    }

    /**
     * 获取成员标识。
     *
     * @return 成员标识
     */
    public String getMember() {
        return member;
    }

    /**
     * 设置成员标识。
     *
     * @param member 成员标识
     */
    public void setMember(String member) {
        this.member = member;
    }

    /**
     * 获取分数。
     *
     * @return 分数
     */
    public double getScore() {
        return score;
    }

    /**
     * 设置分数。
     *
     * @param score 分数
     */
    public void setScore(double score) {
        this.score = score;
    }

    /**
     * 获取名次。
     *
     * @return 名次
     */
    public long getRank() {
        return rank;
    }

    /**
     * 设置名次。
     *
     * @param rank 名次
     */
    public void setRank(long rank) {
        this.rank = rank;
    }

}
