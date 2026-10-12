package com.api.lowcode.engine.spel;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * SpelExecutor 单元测试。
 *
 * <p>覆盖核心分支：Map 变量属性访问（bug 修复点：从 SimpleEvaluationContext 改为
 * StandardEvaluationContext + MapAccessor）、布尔求值、字符串求值、空表达式处理。</p>
 */
public class SpelExecutorTest {

    private SpelExecutor spelExecutor;

    @Before
    public void setUp() {
        spelExecutor = new SpelExecutor();
    }

    // ==================== evaluate ====================

    // 测试场景: 表达式为 null 时返回 null
    @Test
    public void evaluateNullExprReturnsNull() {
        assertNull(spelExecutor.evaluate(null, new HashMap<String, Object>()));
    }

    // 测试场景: 表达式为空字符串时返回 null
    @Test
    public void evaluateEmptyExprReturnsNull() {
        assertNull(spelExecutor.evaluate("", new HashMap<String, Object>()));
    }

    // 测试场景: 表达式为纯空白时返回 null
    @Test
    public void evaluateBlankExprReturnsNull() {
        assertNull(spelExecutor.evaluate("   ", new HashMap<String, Object>()));
    }

    // 测试场景: bug 修复点 —— Map key 作为属性访问（root object + MapAccessor）
    @Test
    public void evaluateMapKeyAsProperty() {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("value", 5);
        Object result = spelExecutor.evaluate("value", vars);
        assertEquals(5, result);
    }

    // 测试场景: bug 修复点 —— Map key 参与布尔比较表达式 value > 0
    @Test
    public void evaluateMapKeyInBooleanExpressionTrue() {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("value", 5);
        Object result = spelExecutor.evaluate("value > 0", vars);
        assertEquals(Boolean.TRUE, result);
    }

    // 测试场景: bug 修复点 —— Map key 参与布尔比较表达式 value > 0 为 false
    @Test
    public void evaluateMapKeyInBooleanExpressionFalse() {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("value", -1);
        Object result = spelExecutor.evaluate("value > 0", vars);
        assertEquals(Boolean.FALSE, result);
    }

    // 测试场景: 通过 #变量名 语法访问显式变量
    @Test
    public void evaluateVariableReferenceSyntax() {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("value", 10);
        Object result = spelExecutor.evaluate("#value * 2", vars);
        assertEquals(20, result);
    }

    // 测试场景: variables 为 null 时仍可对常量表达式求值
    @Test
    public void evaluateConstantExprWithNullVariables() {
        Object result = spelExecutor.evaluate("1 + 1", null);
        assertEquals(2, result);
    }

    // 测试场景: 字符串字面量求值
    @Test
    public void evaluateStringLiteral() {
        Object result = spelExecutor.evaluate("'hello'", null);
        assertEquals("hello", result);
    }

    // 测试场景: Map 中包含 null key 的变量被跳过，不抛异常
    @Test
    public void evaluateWithNullKeyInVariables() {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put(null, "ignored");
        vars.put("value", 3);
        Object result = spelExecutor.evaluate("value", vars);
        assertEquals(3, result);
    }

    // ==================== evaluateBoolean ====================

    // 测试场景: 表达式求值为 null（空表达式）时 evaluateBoolean 返回 false
    @Test
    public void evaluateBooleanNullResultReturnsFalse() {
        assertFalse(spelExecutor.evaluateBoolean("", new HashMap<String, Object>()));
    }

    // 测试场景: 结果为 Boolean.TRUE
    @Test
    public void evaluateBooleanTrue() {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("value", 1);
        assertTrue(spelExecutor.evaluateBoolean("value > 0", vars));
    }

    // 测试场景: 结果为 Boolean.FALSE
    @Test
    public void evaluateBooleanFalse() {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("value", -1);
        assertFalse(spelExecutor.evaluateBoolean("value > 0", vars));
    }

    // 测试场景: 结果为字符串 "true" 解析为 true
    @Test
    public void evaluateBooleanStringTrue() {
        assertTrue(spelExecutor.evaluateBoolean("'true'", null));
    }

    // 测试场景: 结果为字符串 "false" 解析为 false
    @Test
    public void evaluateBooleanStringFalse() {
        assertFalse(spelExecutor.evaluateBoolean("'false'", null));
    }

    // 测试场景: 结果为非布尔字符串（Boolean.parseBoolean 返回 false）
    @Test
    public void evaluateBooleanStringNonBoolean() {
        assertFalse(spelExecutor.evaluateBoolean("'yes'", null));
    }

    // 测试场景: 结果为数字 0 返回 false
    @Test
    public void evaluateBooleanNumberZero() {
        assertFalse(spelExecutor.evaluateBoolean("0", null));
    }

    // 测试场景: 结果为非零数字返回 true
    @Test
    public void evaluateBooleanNumberNonZero() {
        assertTrue(spelExecutor.evaluateBoolean("42", null));
    }

    // 测试场景: 结果为非 Boolean/String/Number 类型（inline list）返回 true
    @Test
    public void evaluateBooleanOtherTypeReturnsTrue() {
        assertTrue(spelExecutor.evaluateBoolean("{'a','b'}", null));
    }

    // ==================== evaluateString ====================

    // 测试场景: 表达式求值为 null 时 evaluateString 返回 null
    @Test
    public void evaluateStringNullReturnsNull() {
        assertNull(spelExecutor.evaluateString("", new HashMap<String, Object>()));
    }

    // 测试场景: 字符串结果原样返回
    @Test
    public void evaluateStringLiteralResult() {
        assertEquals("hello", spelExecutor.evaluateString("'hello'", null));
    }

    // 测试场景: 数字结果转换为字符串
    @Test
    public void evaluateStringNumberResult() {
        assertEquals("123", spelExecutor.evaluateString("123", null));
    }
}