package com.playtika.cards.service;

import com.playtika.cards.domain.Album;
import com.playtika.cards.domain.AlbumSet;
import com.playtika.cards.domain.Card;
import com.playtika.cards.domain.Event;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by rostyslavs on 11/21/2015.
 */
public class DefaultCardAssigner implements CardAssigner {
    private final Album referenceAlbum;
    private List<Consumer> consumers = new ArrayList<>();
    private Map<Long, Album> albumMap = new HashMap<>();
    final private Map<Long, Card> CARD_MAP;
    final private Map<Long, AlbumSet> ALBUM_SET_MAP;

    public DefaultCardAssigner(ConfigurationProvider configurationProvider) {
        this.referenceAlbum = configurationProvider.get();
        Map<Long, Card> cards = new HashMap<>();
        Map<Long, AlbumSet> albumSets = new HashMap<>();
        for (AlbumSet as: referenceAlbum.sets) {
            for (Card c: as.cards) {
                cards.put(c.id, c);
                albumSets.put(c.id, as);
            }
        }
        CARD_MAP = Collections.unmodifiableMap(cards);
        ALBUM_SET_MAP = Collections.unmodifiableMap(albumSets);
    }


    @Override
    public void assignCard(long userId, long cardId) {
        final Card card = CARD_MAP.get(cardId);
        final AlbumSet referenceAlbumSet = ALBUM_SET_MAP.get(cardId);

        Album userAlbum = albumMap.get(userId);
        if (userAlbum == null) {
            synchronized (albumMap) {
                if (albumMap.get(userId) == null) {
                    albumMap.put(userId, createNewAlbum());
                }
            }
            userAlbum = albumMap.get(userId);
        }

        synchronized (userAlbum.sets) {
            final AlbumSet userAlbumSet = userAlbum.sets.stream().filter(as -> as.id == referenceAlbumSet.id).collect(Collectors.toList()).get(0);
            if (!userAlbumSet.cards.contains(card)) {
                userAlbum.sets.remove(userAlbumSet);
                userAlbumSet.cards.add(card);
                userAlbum.sets.add(userAlbumSet);

                if (userAlbumSet.equals(referenceAlbumSet)) {
                    notifyConsumers(new Event(userId, Event.Type.SET_FINISHED));
                }

                if (userAlbum.equals(referenceAlbum)) {
                    notifyConsumers(new Event(userId, Event.Type.ALBUM_FINISHED));
                }
            }
        }

    }

    @Override
    public void subscribe(Consumer<Event> consumer) {
        synchronized (consumers) {
            consumers.add(consumer);
        }
    }

    private void notifyConsumers(Event event) {
        synchronized (consumers) {
            for (Consumer consumer: consumers) {
                consumer.accept(event);
            }
        }
    }

    private Album createNewAlbum() {
        final Album newAlbum = new Album(referenceAlbum.id, referenceAlbum.name, Collections.synchronizedSet(new HashSet<>()));
        for ( AlbumSet aSet : referenceAlbum.sets) {
            newAlbum.sets.add(new AlbumSet(aSet.id, aSet.name, Collections.synchronizedSet(new HashSet<>())));
        }

        return newAlbum;
    }
}
