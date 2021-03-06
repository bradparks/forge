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
package forge.limited;

import forge.card.CardEdition;
import forge.card.IUnOpenedProduct;
import forge.card.UnOpenedProduct;
import forge.deck.CardPool;
import forge.item.PaperCard;
import forge.item.SealedProduct;
import forge.model.CardBlock;
import forge.model.FModel;
import forge.model.UnOpenedMeta;
import forge.properties.ForgeConstants;
import forge.util.FileUtil;
import forge.util.TextUtil;
import forge.util.gui.SGuiChoose;
import forge.util.gui.SOptionPane;

import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * <p>
 * SealedDeckFormat class.
 * </p>
 * 
 * @author Forge
 * @version $Id$
 * @since 1.0.15
 */
public class SealedCardPoolGenerator {
    public static final String FILE_EXT = ".sealed";

    private final ArrayList<IUnOpenedProduct> product = new ArrayList<IUnOpenedProduct>();

    /** The Land set code. */
    private String landSetCode = null;

    /**
     * <p>
     * Constructor for SealedDeck.
     * </p>
     * 
     * @param poolType
     *            a {@link java.lang.String} object.
     */
    public SealedCardPoolGenerator(final LimitedPoolType poolType) {
        switch(poolType) {
            case Full:
                // Choose number of boosters
                if (!chooseNumberOfBoosters(new UnOpenedProduct(SealedProduct.Template.genericBooster))) {
                    return;
                }
                landSetCode = CardEdition.Predicates.getRandomSetWithAllBasicLands(FModel.getMagicDb().getEditions()).getCode();
                break;

            case Block:
            case FantasyBlock:
                List<CardBlock> blocks = new ArrayList<CardBlock>();
                Iterable<CardBlock> src = poolType == LimitedPoolType.Block ? FModel.getBlocks() : FModel.getFantasyBlocks();
                for (CardBlock b : src) {
                    blocks.add(b);
                }

                final CardBlock block = SGuiChoose.oneOrNone("Choose Block", blocks);
                if (block == null) { return; }

                final int nPacks = block.getCntBoostersSealed();
                final Stack<String> sets = new Stack<String>();

                for (CardEdition edition : block.getSets()) {
                    sets.add(edition.getCode());
                }

                for (String ms : block.getMetaSetNames()) {
                    sets.push(ms);
                }

                if (sets.size() > 1 ) {
                    final List<String> setCombos = getSetCombos(sets, nPacks);
                    if (setCombos == null || setCombos.isEmpty()) {
                        throw new RuntimeException("Unsupported amount of packs (" + nPacks + ") in a Sealed Deck block!");
                    }

                    final String p = setCombos.size() > 1 ? SGuiChoose.oneOrNone("Choose packs to play with", setCombos) : setCombos.get(0);
                    if (p == null) { return; }

                    for (String pz : TextUtil.split(p, ',')) {
                        String[] pps = TextUtil.splitWithParenthesis(pz.trim(), ' ');
                        String setCode = pps[pps.length - 1];
                        int nBoosters = pps.length > 1 ? Integer.parseInt(pps[0]) : 1;
                        while (nBoosters-- > 0) {
                            this.product.add(block.getBooster(setCode));
                        }
                    }
                }
                else {
                    IUnOpenedProduct prod = block.getBooster(sets.get(0));
                    for (int i = 0; i < nPacks; i++) {
                        this.product.add(prod);
                    }
                }

                landSetCode = block.getLandSet().getCode();
                break;

            case Custom:
                String[] dList;
                final ArrayList<CustomLimited> customs = new ArrayList<CustomLimited>();

                // get list of custom draft files
                final File dFolder = new File(ForgeConstants.SEALED_DIR);
                if (!dFolder.exists()) {
                    throw new RuntimeException("GenerateSealed : folder not found -- folder is "
                            + dFolder.getAbsolutePath());
                }

                if (!dFolder.isDirectory()) {
                    throw new RuntimeException("GenerateSealed : not a folder -- " + dFolder.getAbsolutePath());
                }

                dList = dFolder.list();

                for (final String element : dList) {
                    if (element.endsWith(FILE_EXT)) {
                        final List<String> dfData = FileUtil.readFile(ForgeConstants.SEALED_DIR + element);
                        final CustomLimited cs = CustomLimited.parse(dfData, FModel.getDecks().getCubes());
                        if (cs.getSealedProductTemplate().getNumberOfCardsExpected() > 5) { // Do not allow too small cubes to be played as 'stand-alone'!
                            customs.add(cs);
                        }
                    }
                }

                // present list to user
                if (customs.isEmpty()) {
                    SOptionPane.showMessageDialog("No custom sealed files found.");
                    return;
                }

                final CustomLimited draft = SGuiChoose.oneOrNone("Choose Custom Sealed Pool", customs);
                if (draft == null) { return; }

                UnOpenedProduct toAdd = new UnOpenedProduct(draft.getSealedProductTemplate(), draft.getCardPool());
                toAdd.setLimitedPool(draft.isSingleton());
                if (!chooseNumberOfBoosters(toAdd)) {
                    return;
                }

                landSetCode = draft.getLandSetCode();
                break;
        }
    }

    private boolean chooseNumberOfBoosters(final IUnOpenedProduct product1) {
        Integer boosterCount = SGuiChoose.getInteger("How many booster packs?", 3, 12);
        if (boosterCount == null) { return false; }

        for (int i = 0; i < boosterCount; i++) {
            this.product.add(product1);
        }
        return true;
    }

    /**
     * <p>
     * getSetCombos.
     * </p>
     * 
     * @return an ArrayList of the set choices.
     */
    private static ArrayList<String> getSetCombos(final List<String> setz, final int nPacks) {
        String[] sets = setz.toArray(ArrayUtils.EMPTY_STRING_ARRAY);
        ArrayList<String> setCombos = new ArrayList<String>();

        if (nPacks == 3) {
            if (sets.length >= 2) {
                setCombos.add(String.format("%s, %s, %s", sets[0], sets[0], sets[0]));
                setCombos.add(String.format("%s, %s, %s", sets[1], sets[0], sets[0]));
                setCombos.add(String.format("%s, %s, %s", sets[1], sets[1], sets[0]));
                setCombos.add(String.format("3 %s", sets[1]));
            }
            if (sets.length >= 3) {
                setCombos.add(String.format("%s, %s, %s", sets[2], sets[0], sets[0]));
                setCombos.add(String.format("%s, %s, %s", sets[0], sets[2], sets[0]));
                setCombos.add(String.format("%s, %s, %s", sets[2], sets[2], sets[2]));
                setCombos.add(String.format("%s, %s, %s", sets[2], sets[1], sets[0]));
            }
        }
        else if (nPacks == 4) {
            if (sets.length >= 2) {
                setCombos.add(String.format("%s, %s, %s, %s", sets[0], sets[0], sets[0], sets[0]));
                setCombos.add(String.format("%s, %s, %s, %s", sets[1], sets[0], sets[0], sets[0]));
                setCombos.add(String.format("%s, %s, %s, %s", sets[1], sets[1], sets[0], sets[0]));
            }
            if (sets.length >= 3) {
                setCombos.add(String.format("%s, %s, %s, %s", sets[2], sets[2], sets[0], sets[0]));
                setCombos.add(String.format("%s, %s, %s, %s", sets[2], sets[1], sets[0], sets[0]));
            }
            if (sets.length >= 4) {
                setCombos.add(String.format("%s, %s, %s, %s", sets[3], sets[2], sets[1], sets[0]));
            }
        }
        else if (nPacks == 5) {
            if (sets.length == 1 || !sets[0].equals(sets[1]) ) {
                setCombos.add(String.format("5 %s", sets[0]));
            }

            if (sets.length >= 2 && !sets[0].equals(sets[1])) {
                setCombos.add(String.format("3 %s, 2 %s", sets[0], sets[1]));
                setCombos.add(String.format("2 %s, 3 %s", sets[0], sets[1]));
            }
            if (sets.length >= 3 && !sets[0].equals(sets[2])) {
                setCombos.add(String.format("3 %s, 2 %s", sets[0], sets[2]));
                setCombos.add(String.format("3 %s, %s, %s", sets[0], sets[1], sets[2]));
                setCombos.add(String.format("2 %s, 2 %s, %s", sets[0], sets[1], sets[2]));
            }
            if (sets.length >= 4) {
                if( sets[1].equals(sets[2]) && sets[1].equals(sets[0])) {
                    setCombos.add(String.format("%s, 4 %s", sets[3], sets[0])); // for guild sealed
                } else {
                    setCombos.add(String.format("%s, %s, %s, 2 %s", sets[3], sets[2], sets[1], sets[0]));
                }
            }
            if (sets.length >= 5) {
                setCombos.add(String.format("%s, %s, %s, %s, %s", sets[4], sets[3], sets[2], sets[1], sets[0]));
            }
        }
        else if (nPacks == 7 && sets.length >= 7) {
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[6], sets[5], sets[4], sets[3], sets[2], sets[1], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[5], sets[4], sets[3], sets[2], sets[1], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[4], sets[3], sets[2], sets[1], sets[1], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[4], sets[3], sets[2], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[3], sets[2], sets[2], sets[1], sets[1], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[3], sets[2], sets[1], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[3], sets[2], sets[1], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[2], sets[2], sets[1], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[2], sets[1], sets[1], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[2], sets[2], sets[2], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[1], sets[1], sets[1], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s", sets[0], sets[0], sets[0], sets[0], sets[0], sets[0], sets[0]));
        }
        else if (nPacks == 8 && sets.length >= 8) {
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[7], sets[6], sets[5], sets[4], sets[3], sets[2], sets[1], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[6], sets[5], sets[4], sets[3], sets[2], sets[1], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[5], sets[4], sets[3], sets[2], sets[1], sets[1], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[5], sets[4], sets[3], sets[2], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[4], sets[3], sets[2], sets[2], sets[1], sets[1], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[4], sets[3], sets[2], sets[1], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[4], sets[3], sets[2], sets[1], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[3], sets[3], sets[2], sets[2], sets[1], sets[1], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[3], sets[2], sets[2], sets[1], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[3], sets[2], sets[1], sets[1], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[3], sets[2], sets[1], sets[0], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[2], sets[2], sets[1], sets[1], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[2], sets[2], sets[2], sets[2], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[1], sets[1], sets[1], sets[1], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s", sets[0], sets[0], sets[0], sets[0], sets[0], sets[0], sets[0], sets[0]));
        }
        else if (nPacks == 9 && sets.length >= 9) {
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[8], sets[7], sets[6], sets[5], sets[4], sets[3], sets[2], sets[1], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[7], sets[6], sets[5], sets[4], sets[3], sets[2], sets[1], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[6], sets[5], sets[4], sets[3], sets[2], sets[1], sets[1], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[6], sets[5], sets[4], sets[3], sets[2], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[4], sets[3], sets[3], sets[2], sets[2], sets[1], sets[1], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[4], sets[3], sets[2], sets[2], sets[1], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[4], sets[3], sets[2], sets[1], sets[1], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[4], sets[3], sets[2], sets[2], sets[1], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[4], sets[3], sets[2], sets[1], sets[1], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[4], sets[3], sets[2], sets[1], sets[0], sets[0], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[3], sets[3], sets[2], sets[2], sets[1], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s", sets[3], sets[2], sets[2], sets[1], sets[1], sets[1], sets[0], sets[0], sets[0]));
            setCombos.add(String.format("%s, %s, 2 %s, 5 %s", sets[3], sets[2], sets[1], sets[0]));
            setCombos.add(String.format("3 %s, 3 %s, 3 %s", sets[2], sets[1], sets[0]));
            setCombos.add(String.format("2 %s, 2 %s, 5 %s", sets[2], sets[1], sets[0]));
            setCombos.add(String.format("%s, %s, 7 %s", sets[2], sets[1], sets[0]));
            setCombos.add(String.format("4 %s, 5 %s", sets[2], sets[0]));
            setCombos.add(String.format("4 %s, 5 %s", sets[1], sets[0]));
            setCombos.add(String.format("9 %s", sets[0]));
        }
        else { // Default to 6 packs
            if (sets.length == 1 || !sets[0].equals(sets[1]) ) {
                setCombos.add(String.format("6 %s", sets[0]));
            }

            if (sets.length >= 2 && !sets[0].equals(sets[1])) {
                setCombos.add(String.format("4 %s, 2 %s", sets[0], sets[1]));
                setCombos.add(String.format("3 %s, 3 %s", sets[0], sets[1]));
            }
            if (sets.length >= 3 && !sets[0].equals(sets[2])) {
                setCombos.add(String.format("3 %s, 3 %s", sets[0], sets[2]));
                setCombos.add(String.format("2 %s, 2 %s, 2 %s", sets[0], sets[1], sets[2]));
            }
            if (sets.length >= 4) {
                if ( sets[1].equals(sets[2]) && sets[1].equals(sets[0])) {
                    setCombos.add(String.format("%s, 5 %s", sets[3], sets[0])); // for guild sealed
                }
                else {
                    setCombos.add(String.format("%s, %s, %s, 3 %s", sets[3], sets[2], sets[1], sets[0]));
                    setCombos.add(String.format("%s, %s, 2 %s, 2 %s", sets[3], sets[2], sets[1], sets[0]));
                }
            }
            if (sets.length >= 5) {
                setCombos.add(String.format("%s, %s, %s, %s, 2 %s", sets[4], sets[3], sets[2], sets[1], sets[0]));
            }
            if (sets.length >= 6) {
                setCombos.add(String.format("%s, %s, %s, %s, %s, %s", sets[5], sets[4], sets[3], sets[2], sets[1], sets[0]));
            }
        }
        return setCombos;
    }

    /**
     * <p>
     * getCardpool.
     * </p>
     * 
     * @param isHuman
     *      boolean, get pool for human (possible choices)
     * @return a {@link forge.CardList} object.
     */
    public CardPool getCardPool(final boolean isHuman) {
        final CardPool pool = new CardPool();

        for (IUnOpenedProduct prod : product) {
            if (prod instanceof UnOpenedMeta) {
                List<PaperCard> cards = ((UnOpenedMeta) prod).open(isHuman, true);
                if (cards == null) {
                    return null; //return null if user canceled
                }
                pool.addAllFlat(cards);
            }
            else {
                pool.addAllFlat(prod.get());
            }
        }
        return pool;
    }

    /**
     * Gets the land set code.
     * 
     * @return the landSetCode
     */
    public String getLandSetCode() {
        return this.landSetCode;
    }

    public boolean isEmpty() {
        return product.isEmpty();
    }
}
