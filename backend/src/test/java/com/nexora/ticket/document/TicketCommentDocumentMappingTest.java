package com.nexora.ticket.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class TicketCommentDocumentMappingTest {

    @Test
    void readsCommentsStoredWithThePreviousPackageName() throws Exception {
        MongoCustomConversions conversions = MongoCustomConversions.create(adapter -> {});
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of(TicketCommentDocument.class));
        context.afterPropertiesSet();

        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();

        Instant createdAt = Instant.parse("2026-10-06T08:00:00Z");
        Document stored = new Document("_id", "existing-comment")
                // Keep the historical type hint: existing MongoDB rows are not rewritten by this refactor.
                .append("_class", "com.nexora.ticket.comment.document.TicketCommentDocument")
                .append("legacySqlId", 42L)
                .append("ticketId", 10L)
                .append("authorId", 20L)
                .append("body", "Existing comment")
                .append("visibility", "INTERNAL")
                .append("createdAt", Date.from(createdAt));

        TicketCommentDocument comment = converter.read(TicketCommentDocument.class, stored);

        assertThat(comment.getId()).isEqualTo("existing-comment");
        assertThat(comment.getLegacySqlId()).isEqualTo(42L);
        assertThat(comment.getTicketId()).isEqualTo(10L);
        assertThat(comment.getAuthorId()).isEqualTo(20L);
        assertThat(comment.getBody()).isEqualTo("Existing comment");
        assertThat(comment.getVisibility()).isEqualTo("INTERNAL");
        assertThat(comment.getCreatedAt()).isEqualTo(createdAt);
    }
}
