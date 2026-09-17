package com.card.Yugioh.service;

import com.card.Yugioh.repository.CardImgRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.nio.file.*;

/** A small shared CSV joins image IDs to parent cards without Python issuing per-card SQL. */
@Service
@RequiredArgsConstructor
public class CardCatalogService {
    private final CardImgRepository images;
    @Value("${card.image.small.save-path}") private String imageDirectory;

    @Transactional(readOnly = true)
    public void export(boolean changed) throws IOException {
        Path path = Path.of(imageDirectory, "catalog.csv");
        if (!changed && Files.isRegularFile(path)) return;
        StringBuilder csv = new StringBuilder("id,card_id,name,type\n");
        for (var image : images.findAllWithCard()) {
            var card = image.getCardModel();
            if (card.getType() == null || card.getType().isBlank()) continue;
            csv.append(image.getId()).append(',').append(card.getId()).append(',')
                .append(quote(card.getName())).append(',').append(quote(card.getType())).append('\n');
        }
        String content = csv.toString();
        if (Files.exists(path) && Files.readString(path).equals(content)) return;
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling("catalog.csv.tmp");
        Files.writeString(temporary, content);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }
}
