package com.api.lowcode.lang;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/lowcode/lang")
public class LangController {

  @Resource
  private LangService langService;

  /** 列出所有启用的语言 */
  @PostMapping("/list")
  public ResMsg list() {
    ResMsg r = new ResMsg();
    try {
      r.setData(langService.listLangs());
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /** 列出所有语言（含禁用）—— 翻译管理页面用 */
  @PostMapping("/list-all")
  public ResMsg listAll() {
    ResMsg r = new ResMsg();
    try {
      r.setData(langService.listAllLangs());
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /** 新增语言。入参：{locale, name, sortOrder, enabled, isRtl} */
  @PostMapping("/add-lang")
  public ResMsg addLang(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      langService.addLang(req);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /** 编辑语言。入参：{locale, name?, sortOrder?, enabled?, isRtl?} */
  @PostMapping("/update-lang")
  public ResMsg updateLang(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      langService.updateLang(req);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /** 删除语言。入参：{locale:"en-US"} */
  @PostMapping("/delete-lang")
  public ResMsg deleteLang(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      String locale = req.getString("locale");
      langService.deleteLang(locale);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /**
   * 加载翻译字典。
   * 入参：{locale:"en-US", modules:["common","home"]}（modules 可选）
   * 出参：{"保存":"Save","首页":"Home",...}
   */
  @PostMapping("/load")
  public ResMsg load(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      String locale = req.getString("locale");
      JSONArray modulesArr = req.getJSONArray("modules");
      List<String> modules = new ArrayList<String>();
      if (modulesArr != null) {
        for (int i = 0; i < modulesArr.size(); i++) {
          modules.add(modulesArr.getString(i));
        }
      }
      r.setData(langService.loadText(locale, modules));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /**
   * 分页查询翻译文本（翻译管理页面用）。
   * 入参：{locale:"en-US", keyword:"保存", page:1, size:50}
   * 出参：{list:[...], total:500}
   */
  @PostMapping("/page")
  public ResMsg page(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      String locale = req.getString("locale");
      String keyword = req.getString("keyword");
      int page = req.getIntValue("page", 1);
      int size = req.getIntValue("size", 50);
      r.setData(langService.pageText(locale, keyword, page, size));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /** 保存单条翻译（upsert）。入参：{locale, textKey, textVal, module?} */
  @PostMapping("/save")
  public ResMsg save(@RequestBody JSONObject item) {
    ResMsg r = new ResMsg();
    try {
      langService.saveText(item);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /** 批量导入翻译。入参：{locale:"en-US", items:[{textKey,textVal,module?},...]} */
  @PostMapping("/import")
  public ResMsg importText(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      String locale = req.getString("locale");
      JSONArray items = req.getJSONArray("items");
      langService.batchImport(locale, items);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /** 删除单条翻译。入参：{id:123} */
  @PostMapping("/delete")
  public ResMsg delete(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      int id = req.getIntValue("id");
      langService.deleteText(id);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /** 自动翻译。入参：{texts:["保存","取消"], targetLang:"en-US"} */
  @PostMapping("/auto-translate")
  public ResMsg autoTranslate(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      JSONArray textsArr = req.getJSONArray("texts");
      String targetLang = req.getString("targetLang");
      List<String> texts = new ArrayList<String>();
      if (textsArr != null) {
        for (int i = 0; i < textsArr.size(); i++) {
          texts.add(textsArr.getString(i));
        }
      }
      r.setData(langService.autoTranslate(texts, targetLang));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /**
   * 导出翻译数据。
   * 入参：{locale:"en-US", modules:["page1","page2"]}（均可选，不传则导出全部）
   */
  @PostMapping("/export")
  public ResMsg exportLang(@RequestBody(required = false) JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      String locale = req != null ? req.getString("locale") : null;
      JSONArray modulesArr = req != null ? req.getJSONArray("modules") : null;
      List<String> modules = new ArrayList<String>();
      if (modulesArr != null) {
        for (int i = 0; i < modulesArr.size(); i++) {
          modules.add(modulesArr.getString(i));
        }
      }
      r.setData(langService.exportLang(locale, modules));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  /** 批量导入翻译数据（跨语言）。入参：{data:[{locale,textKey,textVal,module},...]} */
  @PostMapping("/import-batch")
  public ResMsg importBatch(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      JSONArray data = req.getJSONArray("data");
      int count = langService.importBatch(data);
      JSONObject result = new JSONObject();
      result.put("count", count);
      r.setData(result);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }
}