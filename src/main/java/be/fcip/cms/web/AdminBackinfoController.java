package be.fcip.cms.web;

import be.fcip.cms.persistence.model.AppParamEntity;
import be.fcip.cms.persistence.model.RedirectMessage;
import be.fcip.cms.persistence.service.IAppParamService;
import be.fcip.cms.service.IMailer;
import be.fcip.cms.util.ApplicationUtils;
import be.fcip.cms.util.CmsFileUtils;
import be.fcip.cms.util.CmsUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.cache2k.Cache;
import org.cache2k.operation.CacheControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping(value = "/admin/backinfo")
@PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
@Slf4j
public class AdminBackinfoController {

    // 1,23 mb par 1000 adminContent
    private final static String VIEWPATH = "admin/backinfo/";

    @Autowired private IAppParamService paramService;
    @Autowired private CacheManager cacheManager;
    @Autowired private IMailer mailer;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public String home(ModelMap model, @ModelAttribute("redirectMessage") RedirectMessage redirectMessage) {

        model.put("processors", Runtime.getRuntime().availableProcessors());
        long allocatedMemory      = (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        model.put("totalMemory", FileUtils.byteCountToDisplaySize(Runtime.getRuntime().totalMemory()));
        model.put("maxMemory", FileUtils.byteCountToDisplaySize(Runtime.getRuntime().maxMemory()));
        model.put("allocatedMemory", FileUtils.byteCountToDisplaySize(allocatedMemory));

        /* Get a list of all filesystem roots on this system */
        long publicFolderSize = CmsFileUtils.size(new File(CmsFileUtils.UPLOAD_DIRECTORY_PUBLIC).toPath());
        long privateFolderSize = CmsFileUtils.size(new File(CmsFileUtils.UPLOAD_DIRECTORY_PRIVATE).toPath());
        model.put("publicFolderSize", FileUtils.byteCountToDisplaySize(publicFolderSize));
        model.put("privateFolderSize", FileUtils.byteCountToDisplaySize(privateFolderSize));
        model.put("redirectMessage", redirectMessage);

        model.put("defaultAdminLang", ApplicationUtils.defaultAdminLocale);
        model.put("defaultSiteLang", ApplicationUtils.defaultLocale);
        model.put("siteLangMap", ApplicationUtils.locales.stream().collect(Collectors.toMap(Locale::toString,e -> e)));
        //model.put("adminLang", applicationService.getad());

        long totalEntry = 0;
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = (Cache) cacheManager.getCache(cacheName).getNativeCache();
            // pour info recupérable comme cela --> cache.asMap();
            totalEntry += CacheControl.of(cache).getSize();
        }
        model.put("cache_total", cacheManager.getCacheNames().size());
        model.put("cache_total_entry", totalEntry);
        return VIEWPATH + "home";
    }

    private String getCachePercentageUsage(long maxCache, long usedCache){
        double usageInpercent = ((double)usedCache * 100) / maxCache;
        DecimalFormat df = new DecimalFormat("####0.00");
        return df.format(usageInpercent);
    }

    @RequestMapping(value = "/clearCache/", method = RequestMethod.DELETE)
    @ResponseBody
    public String clearAllCache(ModelMap model) {

        CmsUtils.clearCaches(cacheManager);
        return "{ data : \"\" }";
    }

    @RequestMapping(value = "/clearCache/{name}", method = RequestMethod.DELETE)
    @ResponseBody
    public String clearCache(ModelMap model, @PathVariable("name") String name) {

        org.springframework.cache.Cache cache = cacheManager.getCache(name);
        if(cache == null){
            return "{ error : \"cache not found\" }";
        } else {
            cache.clear();
        }

        return "{ data : \"\" }";
    }

    @RequestMapping(value = "/refreshAssets", method = RequestMethod.GET)
    @ResponseBody
    public Long refreshAssets(){
        CmsUtils.CMS_UNIQUE_NUMBER = new Date().getTime();
        return CmsUtils.CMS_UNIQUE_NUMBER;
    }

    @RequestMapping(value = "/cacheJson", method = RequestMethod.GET)
    @ResponseBody
    public String getjson(HttpServletResponse response) {


        JsonArrayBuilder data = Json.createArrayBuilder();
        JsonObjectBuilder row;
        // reload tree like this : table.ajax.reload()

        for (String cacheName : cacheManager.getCacheNames()) {

            Cache cache = (Cache) cacheManager.getCache(cacheName).getNativeCache();

            long capacity = CacheControl.of(cache).getEntryCapacity();
            long count = CacheControl.of(cache).getSize();
            float usage = (count * 100) / (float)capacity;

            row = Json.createObjectBuilder();
            row.add("DT_RowData", Json.createObjectBuilder().add("id", cacheName));
            row.add("name",cacheName);

            JsonObjectBuilder keys = Json.createObjectBuilder();
            JsonArrayBuilder array= Json.createArrayBuilder();

            for (Object key : cache.keys()) {
                array.add(key.toString());
            }
            // keys.add("capacity", cache.getStatistics().getSize());
            keys.add("keys" , array);
            keys.add("capacity", capacity);

            row.add("keys", keys);
            row.add("used", Math.round(usage) + " %");
            data.add(row);
        }

        return Json.createObjectBuilder().add("data", data).build().toString();
    }

    /*
    @RequestMapping(value = "/email", method = RequestMethod.POST)
    public String config(String email){
        if(!StringUtils.isEmpty(email)){
            try {
                mailer.sendMail(email, "EMAIL test", "This is a test send by the admin for 0 € , évènement");
            } catch (Exception e) {
                log.error("Unable to Send Email", e);
                return "redirect:/admin/backinfo?error";
            }
        }
        return "redirect:/admin/backinfo";
    }
     */

    @PostMapping(value="/addParam")
    public String addParam(String name){

        Optional<AppParamEntity> param = paramService.findOne(name);
        if(!param.isPresent()){
            AppParamEntity p = new AppParamEntity();
            p.setId(name);
            paramService.save(p);
        }
        return "redirect:/admin/backinfo";
    }

    @PostMapping(value="/updateParam")
    public String updateParam(String name, String value){

        Optional<AppParamEntity> param = paramService.findOne(name);
        if(param.isPresent()){
            AppParamEntity p = param.get();
            p.setValue(value);
            paramService.save(p);
        }
        return "redirect:/admin/backinfo";
    }

    @PostMapping(value="/deleteParam")
    public String deleteParam(String name){

        if(IAppParamService.CORE_PARAMS.contains(name)) return "redirect:/admin/backinfo";

        Optional<AppParamEntity> param = paramService.findOne(name);
        param.ifPresent(appParamEntity -> paramService.delete(appParamEntity));
        return "redirect:/admin/backinfo";
    }
}
