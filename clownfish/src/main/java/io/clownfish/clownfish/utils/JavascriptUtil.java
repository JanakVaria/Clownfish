/*
 * Copyright Rainer Sulzbach
 */
package io.clownfish.clownfish.utils;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.Patch;
import io.clownfish.clownfish.dbentities.CfJavascript;
import io.clownfish.clownfish.dbentities.CfJavascriptversion;
import io.clownfish.clownfish.serviceinterface.CfJavascriptService;
import io.clownfish.clownfish.serviceinterface.CfJavascriptversionService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import javax.faces.bean.ViewScoped;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author sulzbachr
 */
@ViewScoped
@Component
public class JavascriptUtil {
    @Autowired CfJavascriptService cfjavascriptService;
    @Autowired CfJavascriptversionService cfjavascriptversionService;
    
    private @Getter @Setter long currentVersion;
    private @Getter @Setter String javascriptContent = "";
    private @Getter @Setter Patch<String> patch = null;
    private @Getter @Setter List<String> source = null;
    private @Getter @Setter List<String> target = null;

    public JavascriptUtil() {
    }

    public String getVersion(long javascriptref, long version) {
        try {
            CfJavascriptversion javascript = cfjavascriptversionService.findByPK(javascriptref, version);
            byte[] decompress = CompressionUtils.decompress(javascript.getContent());
            return new String(decompress, StandardCharsets.UTF_8);
        } catch (IOException | DataFormatException ex) {
            Logger.getLogger(JavascriptUtil.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public boolean hasDifference(CfJavascript selectedJavascript) {
        boolean diff = false;
        try {
            try {
                currentVersion = cfjavascriptversionService.findMaxVersion(selectedJavascript.getId());
            } catch (NullPointerException ex) {
                currentVersion = 0;
            }
            if (currentVersion > 0) {
                javascriptContent = selectedJavascript.getContent();
                String contentVersion = getVersion(selectedJavascript.getId(), currentVersion);
                source = Arrays.asList(javascriptContent.split("\\r?\\n"));
                target = Arrays.asList(contentVersion.split("\\r?\\n"));
                patch = DiffUtils.diff(source, target);
                if (!patch.getDeltas().isEmpty()) {
                    diff = true;
                }
            } else {
                diff = true;
            }
        } catch (DiffException ex) {
            Logger.getLogger(JavascriptUtil.class.getName()).log(Level.SEVERE, null, ex);
        }
        return diff;
    }
}