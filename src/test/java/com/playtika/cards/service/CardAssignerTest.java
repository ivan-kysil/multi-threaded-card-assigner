package com.playtika.cards.service;

import com.playtika.cards.domain.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Sets.newHashSet;
import static com.playtika.cards.domain.Event.Type.ALBUM_FINISHED;
import static com.playtika.cards.domain.Event.Type.SET_FINISHED;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;
import static java.util.stream.LongStream.range;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.mockito.Mockito.*;

/**
 * Created by rostyslavs on 11/21/2015.
 */
@RunWith(MockitoJUnitRunner.class)
public class CardAssignerTest {

    @Mock
    private ConfigurationProvider configurationProvider;

    private final List<Long> users = range(0L, 10L).boxed()
            .collect(toList());

    @Before
    public void configure() {
        when(configurationProvider.get()).thenReturn(new Album(1L, "Animals", newHashSet(
                new AlbumSet(1L, "Birds", newHashSet(new Card(1L, "Eagle"), new Card(2L, "Cormorant"), new Card(3L, "Sparrow"), new Card(4L, "Raven"))),
                new AlbumSet(2L, "Fish", newHashSet(new Card(5L, "Salmon"), new Card(6L, "Mullet"), new Card(7L, "Bream"), new Card(8L, "Marline")))
        )));
    }

    private final static int REPETITIONS = 10000;

    @Test(timeout = 60000L)
    public void assigningCardsToUsers() throws InterruptedException {
        int rep = 0;
        List<Event> events = new CopyOnWriteArrayList<>();

        while (rep++ < REPETITIONS) {
            final ExecutorService executorService = newFixedThreadPool(10);
            final CardAssigner cardAssigner = new DefaultCardAssigner(configurationProvider);
            final Album album = configurationProvider.get();
            final List<Card> allCards = album.sets.stream().map(set -> set.cards).flatMap(Collection::stream).collect(toList());
            events.clear();
            cardAssigner.subscribe(events::add);
            System.out.print("Repetition " + rep);

            int i = 0;
            while (!albumsFinished(events, album)) {
                i++;
                executorService.submit(() -> {
                    Card card = allCards.get(nextInt(0, allCards.size()));
                    Long userId = users.get(nextInt(0, users.size()));
                    cardAssigner.assignCard(userId, card.id);
                });
            }
            System.out.println(", " + i);
            executorService.shutdown();
            executorService.awaitTermination(1000, TimeUnit.SECONDS);
            long countAlbums = events.stream().filter(event -> event.type == ALBUM_FINISHED).count();
            long countSets = events.stream().filter(event -> event.type == SET_FINISHED).count();
            assert countAlbums == users.size() : "Album Events count is wrong " + countAlbums;
            assert countSets == users.size() * album.sets.size(): "AlbumSet Events count is wrong " + countSets;
        }
    }

    private boolean albumsFinished(List<Event> events, Album album) {
        return events.size() == users.size() + users.size() * album.sets.size();
    }
}