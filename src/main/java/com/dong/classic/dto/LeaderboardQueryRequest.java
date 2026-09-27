package com.dong.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 榜单维度查询请求。榜名不传时用默认榜，
 * 这样单榜场景的调用方不用关心榜的存在。
 */
public class LeaderboardQueryRequest {

    /**
     * 榜单名称。
     */
    @NotBlank
    @Size(max = 128)
    private String board = "default";

    /**
     * 获取榜单名称。
     *
     * @return 榜单名称
     */
    public String getBoard() {
        return board;
    }

    /**
     * 设置榜单名称。
     *
     * @param board 榜单名称
     */
    public void setBoard(String board) {
        this.board = board;
    }

}
