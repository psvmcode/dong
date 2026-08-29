package com.dong.lab.tcc.dto;

public class TccResultResponse {

    private boolean committed;

    private String xid;

    private String message;

    public static TccResultResponse committed(String xid) {
        TccResultResponse response = new TccResultResponse();
        response.setCommitted(true);
        response.setXid(xid);
        response.setMessage("confirmed");
        return response;
    }

    public static TccResultResponse rolledBack(String xid, String message) {
        TccResultResponse response = new TccResultResponse();
        response.setCommitted(false);
        response.setXid(xid);
        response.setMessage(message);
        return response;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
