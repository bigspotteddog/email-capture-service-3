package com.fullstackclouddeveloper.emailcaptureservice;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletResponse;

@SpringBootApplication
@RestController
public class EmailCaptureServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailCaptureServiceApplication.class, args);
    }

    @GetMapping("/emails")
    public List<Map<String, Object>> getEmails(@CookieValue(name = "zoom-meeting", required = false) String zoomMeetingCookie) throws ExecutionException, InterruptedException, IOException {
        List<Map<String, Object>> list = new ArrayList<>();

        Firestore db = getFirestore();

        ApiFuture<QuerySnapshot> query = db.collection("emails").get();
        QuerySnapshot querySnapshot = query.get();
        List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();

        for (QueryDocumentSnapshot document : documents) {
            list.add(document.getData());
        }

        return list;
    }

    @GetMapping("/invitation")
    public Map<String, Object> getInvitation(@RequestParam("campaign") String campaign,
            @CookieValue(name = "zoom-meeting", required = false) String zoomMeetingCookie)
            throws InterruptedException, ExecutionException, IOException {

        Firestore db = getFirestore();

        System.out.println(campaign);

        if (zoomMeetingCookie == null) {
            return getInvitationNotFoundMessage();
        }

        CollectionReference emails = db.collection("emails");
        Query q = emails.whereEqualTo("hash", zoomMeetingCookie);
        ApiFuture<QuerySnapshot> querySnapshot = q.get();
        List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();

        if (documents.isEmpty()) {
            return getInvitationNotFoundMessage();
        }

        return documents.get(0).getData();
    }

    @PostMapping("/notify-me")
    public ResponseEntity<Map<String, String>> post(@RequestBody Map<String, String> request,
            @CookieValue(name = "zoom-meeting", required = false) String zoomMeetingCookie)
            throws IOException, ExecutionException, InterruptedException {

        System.out.println(zoomMeetingCookie);

        String campaign = request.get("campaign");
        String email = request.get("email");
        String hash = UUID.randomUUID().toString();

        Map<String, String> data = saveEmail(campaign, email, hash);
        sendResponseEmail(campaign, email);

        String cookieName = "zoom-meeting";
        Duration maxAge = Duration.ofDays(30);

        HttpCookie cookie = getCookie(hash, cookieName, maxAge);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(data);
    }

    private HttpCookie getCookie(String hash, String cookieName, Duration maxAge) {
        HttpCookie cookie = ResponseCookie.from(cookieName, hash)
            .path("/")
            .httpOnly(true)
            .maxAge(maxAge)
            .build();
        return cookie;
    }

    private Map<String, String> saveEmail(String campaign, String email, String hash) throws IOException {
        Firestore db = getFirestore();
        DocumentReference document = db.collection("emails").document();
        Map<String, String> data = new LinkedHashMap<>() {{
            put("campaign", campaign);
            put("email", email);
            put("hash", hash);
        }};
        document.set(data);
        System.out.println(data);
        return data;
    }

    private void sendResponseEmail(String campaign, String email) throws IOException {
        Email from = new Email("support@nobodyelses.com");
        String subject = "Fullstack cloud developer course registration";
        Email to = new Email(email);
        Content content = new Content("text/plain", "You've been added to the list! We'll notify you when registration begins.");
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(System.getenv("SENDGRID_API_KEY"));
        Request send = new Request();
        try {
            send.setMethod(Method.POST);
            send.setEndpoint("mail/send");
            send.setBody(mail.build());
            sg.api(send);
        } catch (IOException ex) {
            throw ex;
        }
    }

    private Firestore getFirestore() throws IOException {
        FirestoreOptions firestoreOptions =
                FirestoreOptions.getDefaultInstance().toBuilder()
                        .setProjectId("email-capture-service-3")
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build();
        return firestoreOptions.getService();
    }

    private Map<String, Object> getInvitationNotFoundMessage() {
        return new HashMap<String, Object>() {{
            put("message", "Invitation not found. Please re-register and try again.");
        }};
    }
}
