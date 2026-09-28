/**
 * SQL 引擎 API 客户端
 * 
 * 前端调用示例 —— 覆盖全部 9 个 REST 接口
 * 
 * 接口清单：
 *   1. POST /api/sql-engine/query         单条查询
 *   2. POST /api/sql-engine/count         查询总数
 *   3. POST /api/sql-engine/insert        单条新增
 *   4. POST /api/sql-engine/batch-insert  批量新增
 *   5. POST /api/sql-engine/update        单条修改
 *   6. POST /api/sql-engine/batch-update  批量修改
 *   7. POST /api/sql-engine/delete        单条删除
 *   8. POST /api/sql-engine/batch-delete  批量删除
 *   9. POST /api/sql-engine/batch-query   批量查询
 */

// ============================================================
// 基础配置
// ============================================================

const SQL_ENGINE_BASE = '/api/sql-engine';

/**
 * 构建请求头，数据源通过 X-DB-Name 指定（可选，默认主库）
 * @param {string} dbName - 数据源名称，如 'default' / 'secondary'，不传走默认
 */
function buildHeaders(dbName) {
  const headers = { 'Content-Type': 'application/json' };
  if (dbName) {
    headers['X-DB-Name'] = dbName;
  }
  return headers;
}

/**
 * 统一 fetch 封装
 */
async function callSqlEngine(endpoint, body, dbName) {
  const resp = await fetch(`${SQL_ENGINE_BASE}${endpoint}`, {
    method: 'POST',
    headers: buildHeaders(dbName),
    body: JSON.stringify(body)
  });
  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
  }
  return resp.json();
}


// ============================================================
// 1. 单条查询
// ============================================================

/**
 * 查询用户列表（带条件 + 分页 + 排序）
 */
async function queryUsersExample() {
  const result = await callSqlEngine('/query', {
    tableName: 'sys_user',
    columns: ['id', 'name', 'age', 'status', 'create_time'],  // 可选，默认 SELECT *
    conditions: [
      { Symbol: 'and' },
      { Id: 'name', Symbol: 'like', Val: '张', TableAlias: 'T' },
      { Symbol: 'and' },
      { Id: 'age', Symbol: '>', Val: '18', TableAlias: 'T' },
      { Symbol: 'or' },
      { Id: 'status', Symbol: '=', Val: 'active', TableAlias: 'T' }
    ],
    orderBy: [
      { column: 'create_time', direction: 'desc' }
    ],
    page: 1,       // 页码（从 1 开始）
    pageSize: 20   // 每页条数
  }, 'default');

  // result 是 JSONArray，每行一个 JSONObject
  console.log(`查询到 ${result.length} 条记录`);
  result.forEach(row => {
    console.log(`  ID=${row.id}, name=${row.name}, status=${row.status}`);
  });
  return result;
}

/**
 * 无条件查询（全表）
 */
async function queryAllExample() {
  const result = await callSqlEngine('/query', {
    tableName: 'sys_dict',
    columns: ['dict_code', 'dict_name'],
    pageSize: 100  // 只取前 100 条
  }, 'default');
  return result;
}


// ============================================================
// 2. 查询总数（配合分页）
// ============================================================

/**
 * 查询满足条件的总行数 —— 用于分页组件
 */
async function countUsersExample() {
  const result = await callSqlEngine('/count', {
    tableName: 'sys_user',
    conditions: [
      { Symbol: 'and' },
      { Id: 'status', Symbol: '=', Val: 'active', TableAlias: 'T' }
    ]
  }, 'default');

  // result = { success: true, total: 1234 }
  console.log(`总记录数: ${result.total}`);
  return result.total;
}


// ============================================================
// 3. 单条新增
// ============================================================

/**
 * 新增一个用户
 */
async function insertUserExample() {
  const result = await callSqlEngine('/insert', {
    tableName: 'sys_user',
    data: {
      name: '张三',
      age: 25,
      status: 'active',
      email: 'zhangsan@example.com',
      create_time: '2026-09-09 12:00:00'
    }
  }, 'default');

  // result = { success: true, affectedRows: 1 }
  console.log(`新增成功，影响行数: ${result.affectedRows}`);
  return result;
}


// ============================================================
// 4. 批量新增
// ============================================================

/**
 * 批量新增用户 —— data 为数组，每元素一行
 * 使用 JDBC addBatch + executeBatch，一次网络请求插入多行
 */
async function batchInsertUsersExample() {
  const result = await callSqlEngine('/batch-insert', {
    tableName: 'sys_user',
    data: [
      { name: '张三', age: 25, status: 'active' },
      { name: '李四', age: 30, status: 'active' },
      { name: '王五', age: 28, status: 'inactive' },
      { name: '赵六', age: 35, status: 'active' },
      { name: '钱七', age: 22, status: 'active' }
    ]
  }, 'default');

  // result = { success: true, affectedRows: 5 }
  console.log(`批量新增成功，影响行数: ${result.affectedRows}`);
  return result;
}


// ============================================================
// 5. 单条修改
// ============================================================

/**
 * 修改用户信息 —— WHERE 条件必填，防止全表更新
 */
async function updateUserExample() {
  const result = await callSqlEngine('/update', {
    tableName: 'sys_user',
    data: {
      status: 'inactive',
      age: 26
    },
    conditions: [
      { Symbol: 'and' },
      { Id: 'id', Symbol: '=', Val: '100', TableAlias: 'T' }
    ]
  }, 'default');

  // result = { success: true, affectedRows: 1 }
  console.log(`修改成功，影响行数: ${result.affectedRows}`);
  return result;
}

/**
 * 多条件修改
 */
async function updateByMultiConditionExample() {
  const result = await callSqlEngine('/update', {
    tableName: 'sys_user',
    data: {
      status: 'frozen'
    },
    conditions: [
      { Symbol: 'and' },
      { Id: 'dept_id', Symbol: '=', Val: '10', TableAlias: 'T' },
      { Symbol: 'and' },
      { Id: 'age', Symbol: '<', Val: '18', TableAlias: 'T' }
    ]
  }, 'default');
  return result;
}


// ============================================================
// 6. 批量修改
// ============================================================

/**
 * 批量修改 —— 每组有独立的 data + conditions
 * 内部使用事务，全成功才提交
 */
async function batchUpdateUsersExample() {
  const result = await callSqlEngine('/batch-update', {
    tableName: 'sys_user',
    batch: [
      {
        data: { status: 'active' },
        conditions: [
          { Id: 'id', Symbol: '=', Val: '1', TableAlias: 'T' }
        ]
      },
      {
        data: { status: 'active', age: 30 },
        conditions: [
          { Id: 'id', Symbol: '=', Val: '2', TableAlias: 'T' }
        ]
      },
      {
        data: { status: 'inactive' },
        conditions: [
          { Id: 'id', Symbol: '=', Val: '3', TableAlias: 'T' }
        ]
      }
    ]
  }, 'default');

  // result = { success: true, affectedRows: 3 }
  console.log(`批量修改成功，影响行数: ${result.affectedRows}`);
  return result;
}


// ============================================================
// 7. 单条删除
// ============================================================

/**
 * 删除用户 —— WHERE 条件必填，防止全表删除
 */
async function deleteUserExample() {
  const result = await callSqlEngine('/delete', {
    tableName: 'sys_user',
    conditions: [
      { Symbol: 'and' },
      { Id: 'id', Symbol: '=', Val: '100', TableAlias: 'T' }
    ]
  }, 'default');

  // result = { success: true, affectedRows: 1 }
  console.log(`删除成功，影响行数: ${result.affectedRows}`);
  return result;
}

/**
 * 范围删除
 */
async function deleteRangeExample() {
  const result = await callSqlEngine('/delete', {
    tableName: 'sys_log',
    conditions: [
      { Symbol: 'and' },
      { Id: 'create_time', Symbol: '<', Val: '2026-01-01 00:00:00', TableAlias: 'T' }
    ]
  }, 'default');
  return result;
}


// ============================================================
// 8. 批量删除
// ============================================================

/**
 * 批量删除 —— 每组有独立的 conditions
 * 内部使用事务，全成功才提交
 */
async function batchDeleteUsersExample() {
  const result = await callSqlEngine('/batch-delete', {
    tableName: 'sys_user',
    batch: [
      {
        conditions: [
          { Id: 'id', Symbol: '=', Val: '1', TableAlias: 'T' }
        ]
      },
      {
        conditions: [
          { Id: 'id', Symbol: '=', Val: '2', TableAlias: 'T' }
        ]
      },
      {
        conditions: [
          { Id: 'id', Symbol: 'in', Val: '3,4,5', TableAlias: 'T' }
        ]
      }
    ]
  }, 'default');

  // result = { success: true, affectedRows: 3 }
  console.log(`批量删除成功，影响行数: ${result.affectedRows}`);
  return result;
}


// ============================================================
// 9. 批量查询
// ============================================================

/**
 * 批量查询 —— 多条 SELECT 同一连接执行
 * 返回 JSONArray，每个元素对应该查询的结果集
 * 适用于 Dashboard 首页：一次请求拿多个统计数据
 */
async function batchQueryDashboardExample() {
  const result = await callSqlEngine('/batch-query', {
    queries: [
      // 查询 1：今日新增用户
      {
        tableName: 'sys_user',
        columns: ['id', 'name', 'create_time'],
        conditions: [
          { Symbol: 'and' },
          { Id: 'create_time', Symbol: '>=', Val: '2026-09-09 00:00:00', TableAlias: 'T' }
        ],
        orderBy: [{ column: 'create_time', direction: 'desc' }],
        pageSize: 10
      },
      // 查询 2：待处理订单
      {
        tableName: 'biz_order',
        columns: ['order_no', 'status', 'amount'],
        conditions: [
          { Symbol: 'and' },
          { Id: 'status', Symbol: '=', Val: 'pending', TableAlias: 'T' }
        ],
        pageSize: 10
      },
      // 查询 3：库存预警
      {
        tableName: 'wms_inventory',
        columns: ['sku', 'warehouse', 'qty'],
        conditions: [
          { Symbol: 'and' },
          { Id: 'qty', Symbol: '<', Val: '10', TableAlias: 'T' }
        ],
        orderBy: [{ column: 'qty', direction: 'asc' }],
        pageSize: 20
      }
    ]
  }, 'default');

  // result 是 JSONArray，3 个元素分别对应 3 个查询
  const [newUsers, pendingOrders, lowStock] = result;
  console.log(`今日新增用户: ${newUsers.length} 条`);
  console.log(`待处理订单: ${pendingOrders.length} 条`);
  console.log(`库存预警: ${lowStock.length} 条`);
  return result;
}


// ============================================================
// 条件 JSON 格式速查表
// ============================================================

/**
 * ┌──────────────────────────────────────────────────────────────┐
 * │                    条件 JSON 格式说明                          │
 * ├──────────────────────────────────────────────────────────────┤
 * │                                                              │
 * │  字段说明：                                                   │
 * │    Symbol     - 连接符或操作符                                │
 * │    Id         - 列名（条件项必填）                            │
 * │    Val        - 值                                            │
 * │    TableAlias - 表别名（可选，如 'T'）                        │
 * │                                                              │
 * │  连接符 Symbol 值：                                          │
 * │    'and'  / 'or'                                             │
 * │                                                              │
 * │  操作符 Symbol 值：                                          │
 * │    =        等于        {"Id":"name","Symbol":"=","Val":"张三"}│
 * │    <>       不等于      {"Id":"status","Symbol":"<>","Val":"0"}│
 * │    <        小于        {"Id":"age","Symbol":"<","Val":"18"} │
 * │    <=       小于等于    {"Id":"age","Symbol":"<=","Val":"18"}│
 * │    >        大于        {"Id":"age","Symbol":">","Val":"18"} │
 * │    >=       大于等于    {"Id":"age","Symbol":">=","Val":"18"}│
 * │    like     模糊匹配    {"Id":"name","Symbol":"like","Val":"张"}  │
 * │               ↑ 自动加 %前%后                                 │
 * │    is null  为空        {"Id":"email","Symbol":"is null"}    │
 * │    is not null 非空     {"Id":"email","Symbol":"is not null"}│
 * │    in       包含        {"Id":"dept","Symbol":"in","Val":"1,2,3"}│
 * │               ↑ 逗号分隔或 JSON 数组                         │
 * │    between  区间        {"Id":"age","Symbol":"between","Val":"18,30"}│
 * │               ↑ 逗号分隔 low,high                            │
 * │                                                              │
 * │  条件数组排列规则：                                           │
 * │    交替排列：[连接符, 条件项, 连接符, 条件项, ...]            │
 * │    首元素可以是连接符（默认 and）或条件项                     │
 * │                                                              │
 * │  示例：(name LIKE '张%' AND age > 18) OR status = 'active'   │
 * │    [                                                         │
 * │      { Symbol: 'and' },                                      │
 * │      { Id: 'name', Symbol: 'like', Val: '张', TableAlias: 'T' },│
 * │      { Symbol: 'and' },                                      │
 * │      { Id: 'age', Symbol: '>', Val: '18', TableAlias: 'T' }, │
 * │      { Symbol: 'or' },                                       │
 * │      { Id: 'status', Symbol: '=', Val: 'active', TableAlias: 'T' }│
 * │    ]                                                         │
 * │                                                              │
 * └──────────────────────────────────────────────────────────────┘
 */


// ============================================================
// Vue 3 组合式 API 集成示例
// ============================================================

/**
 * 在 Vue 3 组件中使用
 */
function vue3UsageExample() {
  // <script setup>
  // import { ref, onMounted } from 'vue'

  // const users = ref([])
  // const total = ref(0)
  // const loading = ref(false)

  // // 查询 + 分页
  // async function loadUsers(page = 1, pageSize = 20) {
  //   loading.value = true
  //   try {
  //     // 并行查询数据 + 总数
  //     const [data, countResult] = await Promise.all([
  //       callSqlEngine('/query', {
  //         tableName: 'sys_user',
  //         columns: ['id', 'name', 'status', 'create_time'],
  //         conditions: buildConditions(),  // 从搜索表单构建
  //         orderBy: [{ column: 'create_time', direction: 'desc' }],
  //         page,
  //         pageSize
  //       }),
  //       callSqlEngine('/count', {
  //         tableName: 'sys_user',
  //         conditions: buildConditions()
  //       })
  //     ])
  //     users.value = data
  //     total.value = countResult.total
  //   } finally {
  //     loading.value = false
  //   }
  // }

  // // 从搜索表单构建条件 JSON
  // function buildConditions() {
  //   const conditions = [{ Symbol: 'and' }]
  //   if (searchForm.name) {
  //     conditions.push({
  //       Id: 'name', Symbol: 'like', Val: searchForm.name, TableAlias: 'T'
  //     })
  //     conditions.push({ Symbol: 'and' })
  //   }
  //   if (searchForm.status) {
  //     conditions.push({
  //       Id: 'status', Symbol: '=', Val: searchForm.status, TableAlias: 'T'
  //     })
  //   }
  //   return conditions.length > 1 ? conditions : []
  // }

  // onMounted(() => loadUsers())
  // </script>
}

/**
 * 在 React 中使用
 */
function reactUsageExample() {
  // import { useState, useEffect, useCallback } from 'react'
  //
  // function useSqlQuery(tableName, initialConditions) {
  //   const [data, setData] = useState([])
  //   const [total, setTotal] = useState(0)
  //   const [loading, setLoading] = useState(false)
  //   const [page, setPage] = useState(1)
  //   const pageSize = 20
  //
  //   const load = useCallback(async (pageNum = page) => {
  //     setLoading(true)
  //     try {
  //       const [rows, countResult] = await Promise.all([
  //         callSqlEngine('/query', {
  //           tableName,
  //           conditions: initialConditions,
  //           page: pageNum,
  //           pageSize,
  //           orderBy: [{ column: 'id', direction: 'desc' }]
  //         }),
  //         callSqlEngine('/count', {
  //           tableName,
  //           conditions: initialConditions
  //         })
  //       ])
  //       setData(rows)
  //       setTotal(countResult.total)
  //     } finally {
  //       setLoading(false)
  //     }
  //   }, [tableName, initialConditions, page])
  //
  //   useEffect(() => { load(1) }, [])
  //
  //   return { data, total, loading, page, setPage: (p) => { setPage(p); load(p) } }
  // }
}


// ============================================================
// 完整业务场景：CRUD 页面
// ============================================================

/**
 * 模拟一个完整的用户管理页面 CRUD 流程
 */
async function fullCrudFlowExample() {
  console.log('=== 开始 CRUD 流程 ===\n');

  // 1. 批量新增
  console.log('1. 批量新增 3 个用户...');
  await callSqlEngine('/batch-insert', {
    tableName: 'sys_user',
    data: [
      { name: '测试用户A', age: 25, status: 'active' },
      { name: '测试用户B', age: 30, status: 'active' },
      { name: '测试用户C', age: 28, status: 'inactive' }
    ]
  });
  console.log('   批量新增完成\n');

  // 2. 查询验证
  console.log('2. 查询刚新增的用户...');
  const users = await callSqlEngine('/query', {
    tableName: 'sys_user',
    columns: ['id', 'name', 'age', 'status'],
    conditions: [
      { Symbol: 'and' },
      { Id: 'name', Symbol: 'like', Val: '测试用户', TableAlias: 'T' }
    ],
    orderBy: [{ column: 'id', direction: 'desc' }],
    pageSize: 10
  });
  console.log(`   查询到 ${users.length} 条\n`);

  // 3. 修改
  if (users.length > 0) {
    const firstUser = users[0];
    console.log(`3. 修改用户 ${firstUser.name} 的状态...`);
    await callSqlEngine('/update', {
      tableName: 'sys_user',
      data: { status: 'frozen' },
      conditions: [
        { Symbol: 'and' },
        { Id: 'id', Symbol: '=', Val: String(firstUser.id), TableAlias: 'T' }
      ]
    });
    console.log('   修改完成\n');
  }

  // 4. 批量查询（Dashboard 场景）
  console.log('4. 批量查询多个统计...');
  const dashboard = await callSqlEngine('/batch-query', {
    queries: [
      { tableName: 'sys_user', columns: ['id'], conditions: [
        { Symbol: 'and' },
        { Id: 'status', Symbol: '=', Val: 'active', TableAlias: 'T' }
      ]},
      { tableName: 'sys_user', columns: ['id'], conditions: [
        { Symbol: 'and' },
        { Id: 'status', Symbol: '=', Val: 'frozen', TableAlias: 'T' }
      ]}
    ]
  });
  console.log(`   active: ${dashboard[0].length}, frozen: ${dashboard[1].length}\n`);

  // 5. 批量删除
  console.log('5. 批量删除测试用户...');
  const deleteResult = await callSqlEngine('/batch-delete', {
    tableName: 'sys_user',
    batch: users.map(u => ({
      conditions: [
        { Id: 'id', Symbol: '=', Val: String(u.id), TableAlias: 'T' }
      ]
    }))
  });
  console.log(`   删除 ${deleteResult.affectedRows} 条\n`);

  console.log('=== CRUD 流程完成 ===');
}

// 执行完整流程（取消注释即可运行）
// fullCrudFlowExample();
