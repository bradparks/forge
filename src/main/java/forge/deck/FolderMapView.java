/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.deck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import forge.deck.io.IDeckReader;
import forge.item.IHasName;

//reads and writeDeck Deck objects
/**
 * <p>
 * DeckManager class.
 * </p>
 * 
 * @author Forge
 * @version $Id: DeckManager.java 13590 2012-01-27 20:46:27Z Max mtg $
 */
public class FolderMapView<T extends IHasName> implements Iterable<T>, IFolderMapView<T> {
    private final Map<String, T> map;

    /**
     * <p>
     * Constructor for DeckManager.
     * </p>
     * 
     * @param deckDir
     *            a {@link java.io.File} object.
     */
    public FolderMapView(IDeckReader<T> io ) {
        this.map = io.readAll();
    }

    /* (non-Javadoc)
     * @see forge.deck.IFolderMapView#isUnique(java.lang.String)
     */
    @Override
    public final boolean isUnique(final String name) {
        return !this.map.containsKey(name);
    }

    /* (non-Javadoc)
     * @see forge.deck.IFolderMapView#get(java.lang.String)
     */
    @Override
    public final T get(final String name) {
        return this.map.get(name);
    }

    /* (non-Javadoc)
     * @see forge.deck.IFolderMapView#getNames()
     */
    @Override
    public final Collection<String> getNames() {
        return new ArrayList<String>(map.keySet());
    }

    protected final Map<String, T> getMap() {
        return map;
    }

    /* (non-Javadoc)
     * @see java.lang.Iterable#iterator()
     */
    /* (non-Javadoc)
     * @see forge.deck.IFolderMapView#iterator()
     */
    @Override
    public Iterator<T> iterator() {
        return this.map.values().iterator();
    }
}
