package dev.ted.jittertravel.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class GeneralController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/read-only")
    public String readOnly() {
        return "read-only";
    }

}
