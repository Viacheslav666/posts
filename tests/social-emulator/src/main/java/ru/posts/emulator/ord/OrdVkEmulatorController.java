package ru.posts.emulator.ord;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** VK ORD health stub ({@code POSTS_ORD_VK_BASE_URL=http://social-emulator:8090/ord}). */
@RestController
@RequestMapping("/ord")
public class OrdVkEmulatorController {

    @GetMapping("/v1/person/my")
    public Map<String, Object> personMy() {
        return Map.of("external_id", "emulator-person", "status", "ok");
    }
}
