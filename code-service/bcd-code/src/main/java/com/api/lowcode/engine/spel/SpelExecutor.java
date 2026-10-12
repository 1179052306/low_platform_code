package com.api.lowcode.engine.spel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class SpelExecutor {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    public Object evaluate(String expr, Map<String, Object> variables) {
        if (expr == null || expr.trim().isEmpty()) {
            return null;
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.addPropertyAccessor(new MapAccessor());
        if (variables != null) {
            context.setRootObject(variables);
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                if (entry.getKey() != null) {
                    context.setVariable(entry.getKey(), entry.getValue());
                }
            }
        }
        Expression expression = parser.parseExpression(expr);
        return expression.getValue(context);
    }

    public boolean evaluateBoolean(String expr, Map<String, Object> variables) {
        Object result = evaluate(expr, variables);
        if (result == null) {
            return false;
        }
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result instanceof String) {
            return Boolean.parseBoolean((String) result);
        }
        if (result instanceof Number) {
            return ((Number) result).doubleValue() != 0;
        }
        return true;
    }

    public String evaluateString(String expr, Map<String, Object> variables) {
        Object result = evaluate(expr, variables);
        return result != null ? result.toString() : null;
    }
}