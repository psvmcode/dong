package com.dong.lab.tcc.service;

import java.util.Map;

public interface TccParticipant {

    String branchId();

    void tryPhase(String xid, Map<String, Object> payload);

    void confirmPhase(String xid, Map<String, Object> payload);

    void cancelPhase(String xid, Map<String, Object> payload);

}
