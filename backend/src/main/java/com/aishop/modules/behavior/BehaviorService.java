package com.aishop.modules.behavior;

import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.behavior.dto.BehaviorEventResponse;
import org.springframework.stereotype.Service;

@Service
public class BehaviorService {

    public BehaviorEventResponse record(BehaviorEventRequest request) {
        return new BehaviorEventResponse(true);
    }
}

