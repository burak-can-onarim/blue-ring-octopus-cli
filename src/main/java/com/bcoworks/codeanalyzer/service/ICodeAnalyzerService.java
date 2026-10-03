package com.bcoworks.codeanalyzer.service;

import dev.langchain4j.service.SystemMessage;
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

    @SystemMessage("""
            Sen uzman bir Java backend geliştiricisisin.
            Kullanıcının isteğine göre sadece temiz, derlenebilir Java kodunu yaz.
            Kesinlikle markdown (```java) kullanma.
            Açıklama, yorum veya giriş cümlesi yazma. Sadece saf kodu döndür.
            """)
    @UserMessage("Şu isteğe uygun bir sınıf yaz: {{prompt}}")
    String generateCode(String prompt);
}