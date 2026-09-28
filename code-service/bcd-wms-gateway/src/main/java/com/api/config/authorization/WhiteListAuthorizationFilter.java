package com.api.config.authorization;

//@Component
//public class WhiteListAuthorizationFilter implements WebFilter {
//
//    @Resource
//    private WhiteListProperties properties;
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
//        ServerHttpRequest request = exchange.getRequest();
//        String path = request.getURI().getPath();
//        PathMatcher pathMatcher = new AntPathMatcher();
//        //白名单路径移除请求头认证信息
//        List<String> urls = properties.getUrls();
//        for (String url : urls) {
//            if (pathMatcher.match(url, path)) {
//                request = exchange.getRequest().mutate().header(HttpHeaders.AUTHORIZATION, "").build();
//                exchange = exchange.mutate().request(request).build();
//                return chain.filter(exchange);
//            }
//        }
//        return chain.filter(exchange);
//    }
//}
