package com.dong.lab.classic.dto;

/**
 * RankItemResponse。
 */
public class RankItemResponse {

    /**
     * member。
     */
    private String member;

    /**
     * score。
     */
    private double score;

    /**
     * rank。
     */
    private long rank;

    /**
     * of。
     */
    public static RankItemResponse of(String member, double score, long rank) {
        RankItemResponse response = new RankItemResponse();
        response.setMember(member);
        response.setScore(score);
        response.setRank(rank);
        return response;
    }

    /**
     * getMember。
     */
    public String getMember() {
        return member;
    }

    /**
     * setMember。
     */
    public void setMember(String member) {
        this.member = member;
    }

    /**
     * getScore。
     */
    public double getScore() {
        return score;
    }

    /**
     * setScore。
     */
    public void setScore(double score) {
        this.score = score;
    }

    /**
     * getRank。
     */
    public long getRank() {
        return rank;
    }

    /**
     * setRank。
     */
    public void setRank(long rank) {
        this.rank = rank;
    }

}
