package com.card.Yugioh;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.card.Yugioh.service.JobService;
import com.card.Yugioh.service.QueueJobService;

@SpringBootTest(properties = {
    "card.image.save-path=build/test-images",
    "card.image.small.save-path=build/test-images/small",
    "cors.allowedOrigins=http://localhost:3000",
    "ai.predict.baseUrl=http://localhost:5000",
    "spring.datasource.url=jdbc:h2:mem:yugioh-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "cloud.aws.region.static=ap-northeast-2",
    "cloud.aws.stack.auto=false"
})
@ActiveProfiles("test")
class YugiohApplicationTests {

    @MockBean
    private JobService jobService;

    @MockBean
    private QueueJobService queueJobService;

	@Test
	void contextLoads() {
	}

}
