package com.dong.lab.classic.dto;

public class RankItemResponse {

    private String member;

    private double score;

    private long rank;

    public static RankItemResponse of(String member, double score, long rank) {
        RankItemResponse response = new RankItemResponse();
        response.setMember(member);
        response.setScore(score);
        response.setRank(rank);
        return response;
    }

    public String getMember() {
        return member;
    }

    public void setMember(String member) {
        this.member = member;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public long getRank() {
        return rank;
    }

    public void setRank(long rank) {
        this.rank = rank;
    }

}
