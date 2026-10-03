package com.bcoworks.codeanalyzer.service;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface ICodeAnalyzerService {

    @UserMessage("""
             Sen kıdemli bir yazılım mimarısın.
             Aşağıda verilen kodu incele.
            
             Kriterler:
             1. Güvenlik açığı veya potansiyel bug var mı?
             2. Performans riski var mı?
             3. Temiz kod (Clean Code) ilkelerine uyum durumu nedir?
            
             Kodu kısa, net ve somut önerilerle analiz et.
            
             Kod:
             {{code}}
            """)
    String analyze(String code);
}