//package com.security.controller;
//
//import com.nimbusds.jose.jwk.JWKSet;
//import com.nimbusds.jose.jwk.RSAKey;
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.security.KeyPair;
//import java.security.interfaces.RSAPublicKey;
//import java.util.Map;
//
//@RestController
//@RequiredArgsConstructor
//public class PublicKeyController {
//
//    private final KeyPair keyPair;
//
//    @GetMapping("/getPublicKey")
//    public Map<String, Object> getPublicKey() {
//        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
//        RSAKey key = new RSAKey.Builder(publicKey).build();
//        return new JWKSet(key).toJSONObject();
//    }
//
//}
