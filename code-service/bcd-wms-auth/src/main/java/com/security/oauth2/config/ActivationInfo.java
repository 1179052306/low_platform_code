package com.security.oauth2.config;

import com.security.model.ActivationModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lw
 * @date: 2025/1/3
 * @description:
 **/

public class ActivationInfo {

    public static List<ActivationModel> activationInfo = new ArrayList<>();


    public static Map<String, List<ActivationModel>> tenantActivationInfo = new HashMap<>();
}
