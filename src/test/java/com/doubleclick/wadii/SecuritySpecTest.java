package com.doubleclick.wadii;

import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.TestApi.Actor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Authorization boundaries and input handling (global principles, section 2). */
class SecuritySpecTest extends IntegrationTest {

    @Test
    void regularUsersCannotReachAdminEndpoints() {
        Actor user = api.customer();
        for (String path : new String[]{"/api/v1/admin/users", "/api/v1/admin/ledger", "/api/v1/admin/disputes",
                "/api/v1/admin/reports/summary", "/api/v1/admin/audit", "/api/v1/admin/config/COMMISSION"}) {
            assertThat(api.get(path, user).status()).as(path).isEqualTo(403);
        }
        assertThat(api.post("/api/v1/admin/users/" + user.userId() + "/suspend", user, Map.of("reason", "x")).status()).isEqualTo(403);
    }

    @Test
    void staffAccountsCannotUseCustomerOrProviderFlows() {
        Actor admin = api.admin();
        assertThat(api.put("/api/v1/provider/application", admin, Map.of("businessName", "X", "phone", "0223456789",
                "areaId", 1, "address", "x", "categoryIds", java.util.List.of())).status()).isEqualTo(403);
    }

    @Test
    void searchInputIsTreatedAsDataNotQuery() {
        var r = api.get("/api/v1/listings?q=%27%20OR%201%3D1%20--", null).ok();
        assertThat(r.data().path("items")).isEmpty();
        assertThat(api.get("/api/v1/listings?category=NOT_A_CATEGORY", null).code()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void uploadsAreCheckedBySignatureNotJustExtension() {
        Actor user = api.customer();
        var fake = MockMvcRequestBuilders.multipart("/api/v1/files")
                .file(new MockMultipartFile("file", "evil.png", "image/png", "<script>alert(1)</script>".getBytes()))
                .param("purpose", "EVIDENCE").contentType(MediaType.MULTIPART_FORM_DATA);
        assertThat(api.rawStatus(fake, user)).isEqualTo(400);
        var exe = MockMvcRequestBuilders.multipart("/api/v1/files")
                .file(new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[]{'M', 'Z'}))
                .param("purpose", "EVIDENCE");
        assertThat(api.rawStatus(exe, user)).isEqualTo(400);
    }

    @Test
    void unattachedFilesCannotBeReusedByOtherUsers() {
        Actor owner = api.customer();
        Actor other = api.customer();
        String key = api.upload(owner, "EVIDENCE");
        var r = api.post("/api/v1/listings", other, Map.of("title", "Stolen photo", "category", "OTHER", "condition", "GOOD",
                "description", "x", "price", 10, "areaId", 1, "contactMethod", "CHAT", "photoKeys", java.util.List.of(key)));
        assertThat(r.code()).isEqualTo("VALIDATION_FAILED");
    }
}
