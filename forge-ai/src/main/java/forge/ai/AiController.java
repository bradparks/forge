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
package forge.ai;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.esotericsoftware.minlog.Log;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import forge.card.CardType;
import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.GameEntity;
import forge.game.GlobalRuleChange;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.ability.SpellApiBased;
import forge.game.card.Card;
import forge.game.card.CardFactoryUtil;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.card.CardPredicates.Presets;
import forge.game.card.CounterType;
import forge.game.combat.Combat;
import forge.game.cost.Cost;
import forge.game.cost.CostDiscard;
import forge.game.cost.CostPart;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.replacement.ReplaceMoved;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.Ability;
import forge.game.spellability.AbilityManaPart;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.OptionalCost;
import forge.game.spellability.Spell;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellPermanent;
import forge.game.spellability.TargetRestrictions;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Aggregates;
import forge.util.Expressions;
import forge.util.MyRandom;

/**
 * <p>
 * ComputerAI_General class.
 * </p>
 * 
 * @author Forge
 * @version $Id$
 */
public class AiController {

    private final Player player;
    private final Game game;
    public boolean bCheatShuffle;

    public boolean canCheatShuffle() {
        return bCheatShuffle;
    }

    public void allowCheatShuffle(boolean canCheatShuffle) {
        this.bCheatShuffle = canCheatShuffle;
    }

    public Game getGame()
    {
        return game;
    }

    public Player getPlayer()
    {
        return player;
    }

    /**
     * <p>
     * Constructor for ComputerAI_General.
     * </p>
     */
    public AiController(final Player computerPlayer, final Game game0) {
        player = computerPlayer;
        game = game0;
    }

    /**
     * <p>
     * getAvailableSpellAbilities.
     * </p>
     * 
     * @return a {@link forge.CardList} object.
     */
    private List<Card> getAvailableCards() {
        List<Card> all = new ArrayList<Card>(player.getCardsIn(ZoneType.Hand));

        all.addAll(player.getCardsIn(ZoneType.Graveyard));
        all.addAll(player.getCardsIn(ZoneType.Command));
        if (!player.getCardsIn(ZoneType.Library).isEmpty()) {
            all.add(player.getCardsIn(ZoneType.Library).get(0));
        }
        for(Player p : game.getPlayers()) {
            all.addAll(p.getCardsIn(ZoneType.Exile));
            all.addAll(p.getCardsIn(ZoneType.Battlefield));
        }
        return all;
    }

    /**
     * <p>
     * getPossibleETBCounters.
     * </p>
     * 
     * @return a {@link java.util.ArrayList} object.
     */
    private ArrayList<SpellAbility> getPossibleETBCounters() {

        final Player opp = player.getOpponent();
        List<Card> all = new ArrayList<Card>(player.getCardsIn(ZoneType.Hand));
        all.addAll(player.getCardsIn(ZoneType.Exile));
        all.addAll(player.getCardsIn(ZoneType.Graveyard));
        if (!player.getCardsIn(ZoneType.Library).isEmpty()) {
            all.add(player.getCardsIn(ZoneType.Library).get(0));
        }
        all.addAll(opp.getCardsIn(ZoneType.Exile));

        final ArrayList<SpellAbility> spellAbilities = new ArrayList<SpellAbility>();
        for (final Card c : all) {
            for (final SpellAbility sa : c.getNonManaSpellAbilities()) {
                if (sa instanceof SpellPermanent) {
                    sa.setActivatingPlayer(player);
                    if (checkETBEffects(c, sa, ApiType.Counter)) {
                        spellAbilities.add(sa);
                    }
                }
            }
        }
        return spellAbilities;
    }

    private boolean checkCurseEffects(final SpellAbility sa) {
        for (final Card c : game.getCardsIn(ZoneType.Battlefield)) {
            if (c.hasSVar("CurseEffect")) {
                final String curse = c.getSVar("CurseEffect");
                if ("NonActive".equals(curse) && !player.equals(game.getPhaseHandler().getPlayerTurn())) {
                    return true;
                } else if ("DestroyCreature".equals(curse) && sa.isSpell() && sa.getHostCard().isCreature()
                        && !sa.getHostCard().hasKeyword("Indestructible")) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkETBEffects(final Card card, final SpellAbility sa, final ApiType api) {
        boolean rightapi = false;

        if (card.isCreature()
                && game.getStaticEffects().getGlobalRuleChange(GlobalRuleChange.noCreatureETBTriggers)) {
            return api == null;
        }

        // Trigger play improvements
        for (final Trigger tr : card.getTriggers()) {
            // These triggers all care for ETB effects

            final Map<String, String> params = tr.getMapParams();
            if (tr.getMode() != TriggerType.ChangesZone) {
                continue;
            }

            if (!params.get("Destination").equals(ZoneType.Battlefield.toString())) {
                continue;
            }

            if (params.containsKey("ValidCard")) {
                if (!params.get("ValidCard").contains("Self")) {
                    continue;
                }
                if (params.get("ValidCard").contains("notkicked")) {
                    if (sa.isKicked()) {
                        continue;
                    }
                } else if (params.get("ValidCard").contains("kicked")) {
                    if (params.get("ValidCard").contains("kicked ")) { // want a specific kicker
                        String s = params.get("ValidCard").split("kicked ")[1];
                        if ( "1".equals(s) && !sa.isOptionalCostPaid(OptionalCost.Kicker1)) continue;
                        if ( "2".equals(s) && !sa.isOptionalCostPaid(OptionalCost.Kicker2)) continue;
                    } else if (!sa.isKicked()) { 
                        continue;
                    }
                }
            }

            if (!tr.requirementsCheck(game)) {
                continue;
            }

            if (tr.getOverridingAbility() != null) {
                // Abilities yet
                continue;
            }
            
            // if trigger is not mandatory - no problem
            if (params.get("OptionalDecider") != null) {
                continue;
            }

            // Maybe better considerations
            final String execute = params.get("Execute");
            if (execute == null) {
                continue;
            }
            final SpellAbility exSA = AbilityFactory.getAbility(card.getSVar(execute), card);

            if (api != null) {
                if (exSA.getApi() != api) {
                    continue;
                } else {
                    rightapi = true;
                }
            }

            if (sa != null) {
                exSA.setActivatingPlayer(sa.getActivatingPlayer());
            }
            else {
                exSA.setActivatingPlayer(player);
            }
            exSA.setTrigger(true);

            // Run non-mandatory trigger.
            // These checks only work if the Executing SpellAbility is an Ability_Sub.
            if ((exSA instanceof AbilitySub) && !doTrigger(exSA, false)) {
                // AI would not run this trigger if given the chance
                return false;
            }
        }
        if (api != null && !rightapi) {
            return false;
        }

        // Replacement effects
        for (final ReplacementEffect re : card.getReplacementEffects()) {
            // These Replacements all care for ETB effects

            final Map<String, String> params = re.getMapParams();
            if (!(re instanceof ReplaceMoved)) {
                continue;
            }

            if (!params.get("Destination").equals(ZoneType.Battlefield.toString())) {
                continue;
            }

            if (params.containsKey("ValidCard")) {
                if (!params.get("ValidCard").contains("Self")) {
                    continue;
                }
                if (params.get("ValidCard").contains("notkicked")) {
                    if (sa.isKicked()) {
                        continue;
                    }
                } else if (params.get("ValidCard").contains("kicked")) {
                    if (params.get("ValidCard").contains("kicked ")) { // want a specific kicker
                        String s = params.get("ValidCard").split("kicked ")[1];
                        if ( "1".equals(s) && !sa.isOptionalCostPaid(OptionalCost.Kicker1)) continue;
                        if ( "2".equals(s) && !sa.isOptionalCostPaid(OptionalCost.Kicker2)) continue;
                    } else if (!sa.isKicked()) { // otherwise just any must be present
                        continue;
                    }
                }
            }

            if (!re.requirementsCheck(game)) {
                continue;
            }
            final SpellAbility exSA = re.getOverridingAbility();

            if (exSA != null) {
                if (sa != null) {
                    exSA.setActivatingPlayer(sa.getActivatingPlayer());
                }
                else {
                    exSA.setActivatingPlayer(player);
                }

                if (exSA.getActivatingPlayer() == null) {
                    throw new InvalidParameterException("Executing SpellAbility for Replacement Effect has no activating player");
                }
            }

            // ETBReplacement uses overriding abilities.
            // These checks only work if the Executing SpellAbility is an Ability_Sub.
            if (exSA != null && (exSA instanceof AbilitySub) && !doTrigger(exSA, false)) {
                return false;
            }
        }

        return true;
    }
    
    
    private ArrayList<SpellAbility> getOriginalAndAltCostAbilities(final ArrayList<SpellAbility> originList)
    {
        final ArrayList<SpellAbility> newAbilities = new ArrayList<SpellAbility>();
        for (SpellAbility sa : originList) {
            sa.setActivatingPlayer(player);
            //add alternative costs as additional spell abilities
            newAbilities.add(sa);
            newAbilities.addAll(GameActionUtil.getAlternativeCosts(sa));
        }
    
        final ArrayList<SpellAbility> result = new ArrayList<SpellAbility>();
        for (SpellAbility sa : newAbilities) {
            sa.setActivatingPlayer(player);
            result.addAll(GameActionUtil.getOptionalCosts(sa));
        }
        return result;
    }

    /**
     * Returns the spellAbilities from the card list.
     * 
     * @param l
     *            a {@link forge.CardList} object.
     * @return an array of {@link forge.game.spellability.SpellAbility} objects.
     */
    private ArrayList<SpellAbility> getSpellAbilities(final List<Card> l) {
        final ArrayList<SpellAbility> spellAbilities = new ArrayList<SpellAbility>();
        for (final Card c : l) {
            for (final SpellAbility sa : c.getSpellAbilities()) {
                spellAbilities.add(sa);
            }
        }
        return spellAbilities;
    }

    /**
     * <p>
     * getPlayableCounters.
     * </p>
     * 
     * @param l
     *            a {@link forge.CardList} object.
     * @return a {@link java.util.ArrayList} object.
     */
    private ArrayList<SpellAbility> getPlayableCounters(final List<Card> l) {
        final ArrayList<SpellAbility> spellAbility = new ArrayList<SpellAbility>();
        for (final Card c : l) {
            for (final SpellAbility sa : c.getNonManaSpellAbilities()) {
                // Check if this AF is a Counterpsell
                if (sa.getApi() == ApiType.Counter) {
                    spellAbility.add(sa);
                }
            }
        }

        return spellAbility;
    }

    // plays a land if one is available
    /**
     * <p>
     * chooseLandsToPlay.
     * </p>
     * 
     * @return a boolean.
     */
    public List<Card> getLandsToPlay() {
    
        final List<Card> hand = new ArrayList<Card>(player.getCardsIn(ZoneType.Hand));
        hand.addAll(player.getCardsIn(ZoneType.Exile));
        List<Card> landList = CardLists.filter(hand, Presets.LANDS);
        List<Card> nonLandList = CardLists.filter(hand, Predicates.not(CardPredicates.Presets.LANDS));
        
        //filter out cards that can't be played
        landList = CardLists.filter(landList, new Predicate<Card>() {
            @Override
            public boolean apply(final Card c) {
                if (!c.getSVar("NeedsToPlay").isEmpty()) {
                    final String needsToPlay = c.getSVar("NeedsToPlay");
                    List<Card> list = game.getCardsIn(ZoneType.Battlefield);

                    list = CardLists.getValidCards(list, needsToPlay.split(","), c.getController(), c);
                    if (list.isEmpty()) {
                        return false;
                    }
                }
                return player.canPlayLand(c);
            }
        });
    
        final List<Card> landsNotInHand = new ArrayList<Card>(player.getCardsIn(ZoneType.Graveyard));
        if (!player.getCardsIn(ZoneType.Library).isEmpty()) {
            landsNotInHand.add(player.getCardsIn(ZoneType.Library).get(0));
        }
        for (final Card crd : landsNotInHand) {
            if (crd.isLand() && crd.hasKeyword("May be played")) {
                landList.add(crd);
            }
        }
        if (landList.isEmpty()) {
            return null;
        }
        if (landList.size() == 1 && nonLandList.size() < 3) {
            List<Card> cardsInPlay = player.getCardsIn(ZoneType.Battlefield);
            List<Card> landsInPlay = CardLists.filter(cardsInPlay, Presets.LANDS);
            List<Card> allCards = new ArrayList<Card>(player.getCardsIn(ZoneType.Graveyard));
            allCards.addAll(player.getCardsIn(ZoneType.Command));
            allCards.addAll(cardsInPlay);
            int maxCmcInHand = Aggregates.max(hand, CardPredicates.Accessors.fnGetCmc);
            int max = Math.max(maxCmcInHand, 6);
            // consider not playing lands if there are enough already and an ability with a discard cost is present
            if (landsInPlay.size() + landList.size() > max) {
                for (Card c : allCards) {
                    for (SpellAbility sa : c.getSpellAbilities()) {
                        if (sa.getPayCosts() != null) {
                            for (CostPart part : sa.getPayCosts().getCostParts()) {
                                if (part instanceof CostDiscard) {
                                    return null;
                                }
                            }
                        }
                    }
                }
            }
        }
    
        landList = CardLists.filter(landList, new Predicate<Card>() {
            @Override
            public boolean apply(final Card c) {
                canPlaySpellBasic(c);
                if (c.isType("Legendary") && !c.getName().equals("Flagstones of Trokair")) {
                    final List<Card> list = player.getCardsIn(ZoneType.Battlefield);
                    if (Iterables.any(list, CardPredicates.nameEquals(c.getName()))) {
                        return false;
                    }
                }
    
                // don't play the land if it has cycling and enough lands are
                // available
                final ArrayList<SpellAbility> spellAbilities = c.getSpellAbilities();
    
                final List<Card> hand = player.getCardsIn(ZoneType.Hand);
                List<Card> lands = player.getCardsIn(ZoneType.Battlefield);
                lands.addAll(hand);
                lands = CardLists.filter(lands, CardPredicates.Presets.LANDS);
                int maxCmcInHand = Aggregates.max(hand, CardPredicates.Accessors.fnGetCmc);
                for (final SpellAbility sa : spellAbilities) {
                    if (sa.isCycling()) {
                        if (lands.size() >= Math.max(maxCmcInHand, 6)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        });
    
        return landList;
    }

    public Card chooseBestLandToPlay(List<Card> landList)
    {
        if (landList.isEmpty())
            return null;
    
        //Skip reflected lands.
        List<Card> unreflectedLands = new ArrayList<Card>(landList);
        for (Card l : landList) {
            if (l.isReflectedLand()) {
                unreflectedLands.remove(l);
            }
        }

        if (!unreflectedLands.isEmpty()) {
            landList = unreflectedLands;
        }

        // Choose first land to be able to play a one drop
        if (player.getLandsInPlay().isEmpty()) {
            List<Card> oneDrops = CardLists.filter(player.getCardsIn(ZoneType.Hand), CardPredicates.hasCMC(1));
            for (int i = 0; i < MagicColor.WUBRG.length; i++) {
                byte color = MagicColor.WUBRG[i];
                if (!CardLists.filter(oneDrops, CardPredicates.isColor(color)).isEmpty()) {
                    for (Card land : landList) {
                        // Don't play ETB Tapped land if you have a 1 drop can be played
                        // Is this the best way to check if a land ETB Tapped?
                        if (land.hasSVar("ETBTappedSVar")) {
                            continue;
                        }

                        if (land.isType(MagicColor.Constant.BASIC_LANDS.get(i)))
                            return land;
                        
                        for (final SpellAbility m : ComputerUtilMana.getAIPlayableMana(land)) {
                            AbilityManaPart mp = m.getManaPart();
                            if (mp.canProduce(MagicColor.toShortString(color), m)) {
                                return land;
                            }
                        }
                    }
                }
            }
        }

        //play basic lands that are needed the most
        if (Iterables.any(landList, CardPredicates.Presets.BASIC_LANDS)) {
            final List<Card> combined = player.getCardsIn(ZoneType.Battlefield);
    
            final ArrayList<String> basics = new ArrayList<String>();
    
            // what types can I go get?
            for (final String name : CardType.Constant.BASIC_TYPES) {
                if (Iterables.any(landList, CardPredicates.isType(name))) {
                    basics.add(name);
                }
            }
    
            // Which basic land is least available from hand and play, that I still
            // have in my deck
            int minSize = Integer.MAX_VALUE;
            String minType = null;
    
            for (int i = 0; i < basics.size(); i++) {
                final String b = basics.get(i);
                final int num = CardLists.getType(combined, b).size();
                if (num < minSize) {
                    minType = b;
                    minSize = num;
                }
            }
    
            if (minType != null) {
                landList = CardLists.getType(landList, minType);
            }
        }
        return landList.get(0);
    }

    // if return true, go to next phase
    /**
     * <p>
     * playCounterSpell.
     * </p>
     * 
     * @param possibleCounters
     *            a {@link java.util.ArrayList} object.
     * @return a boolean.
     */
    private SpellAbility chooseCounterSpell(final ArrayList<SpellAbility> possibleCounters) {
        if ( possibleCounters == null || possibleCounters.isEmpty())
            return null;;
        
        SpellAbility bestSA = null;
        int bestRestriction = Integer.MIN_VALUE;


        for (final SpellAbility sa : getOriginalAndAltCostAbilities(possibleCounters)) {
            SpellAbility currentSA = sa;
            sa.setActivatingPlayer(player);
            // check everything necessary
            
            
            AiPlayDecision opinion = canPlayAndPayFor(currentSA);
            //PhaseHandler ph = game.getPhaseHandler();
            // System.out.printf("Ai thinks '%s' of %s @ %s %s >>> \n", opinion, sa, Lang.getPossesive(ph.getPlayerTurn().getName()), ph.getPhase());
            if (opinion == AiPlayDecision.WillPlay) {
                if (bestSA == null) {
                    bestSA = currentSA;
                    bestRestriction = ComputerUtil.counterSpellRestriction(player, currentSA);
                } else {
                    // Compare bestSA with this SA
                    final int restrictionLevel = ComputerUtil.counterSpellRestriction(player, currentSA);
    
                    if (restrictionLevel > bestRestriction) {
                        bestRestriction = restrictionLevel;
                        bestSA = currentSA;
                    }
                }
            }
        }

        // TODO - "Look" at Targeted SA and "calculate" the threshold
        // if (bestRestriction < targetedThreshold) return false;
        return bestSA;
    } // playCounterSpell()

    // This is for playing spells regularly (no Cascade/Ripple etc.)
    private AiPlayDecision canPlayAndPayFor(final SpellAbility sa) {
        if (!sa.canPlay()) {
            return AiPlayDecision.CantPlaySa;
        }

        AiPlayDecision op = canPlaySa(sa);
        if (op != AiPlayDecision.WillPlay) {
            return op;
        }
        return ComputerUtilCost.canPayCost(sa, player) ? AiPlayDecision.WillPlay : AiPlayDecision.CantAfford;
    }
    
    public AiPlayDecision canPlaySa(SpellAbility sa) {
        final Card card = sa.getHostCard();
        if ( sa instanceof WrappedAbility ) {
            return canPlaySa(((WrappedAbility) sa).getWrappedAbility());
        }
        if( sa.getApi() != null ) {
            boolean canPlay = SpellApiToAi.Converter.get(sa.getApi()).canPlayAIWithSubs(player, sa);
            if(!canPlay) 
                return AiPlayDecision.CantPlayAi;
        } else if (sa.getPayCosts() != null){
            Cost payCosts = sa.getPayCosts();
            ManaCost mana = payCosts.getTotalMana();
            if (mana!= null && mana.countX() > 0) {
                // Set PayX here to maximum value.
                final int xPay = ComputerUtilMana.determineLeftoverMana(sa, player);
                if (xPay <= 0) {
                    return AiPlayDecision.CantAffordX;
                }
                card.setSVar("PayX", Integer.toString(xPay));
            }
        }
        if (checkCurseEffects(sa)) {
            return AiPlayDecision.CurseEffects;
        }
        if( sa instanceof SpellPermanent ) {
            ManaCost mana = sa.getPayCosts().getTotalMana();
            if (mana.countX() > 0) {
                // Set PayX here to maximum value.
                final int xPay = ComputerUtilMana.determineLeftoverMana(sa, player);
                if (xPay <= 0) {
                    return AiPlayDecision.CantAffordX;
                }
                card.setSVar("PayX", Integer.toString(xPay));
            }
            
            // Check for valid targets before casting
            if (card.getSVar("OblivionRing").length() > 0) {
                SpellAbility effectExile = AbilityFactory.getAbility(card.getSVar("TrigExile"), card);
                final ZoneType origin = ZoneType.listValueOf(effectExile.getParam("Origin")).get(0);
                final TargetRestrictions tgt = effectExile.getTargetRestrictions();
                final List<Card> list = CardLists.getValidCards(game.getCardsIn(origin), tgt.getValidTgts(), player, card);
                if (CardLists.getTargetableCards(list, sa).isEmpty()) {
                    return AiPlayDecision.AnotherTime;
                }
            }
            
            // Prevent the computer from summoning Ball Lightning type creatures after attacking
            if (card.hasSVar("EndOfTurnLeavePlay")
                    && (game.getPhaseHandler().isPlayerTurn(player.getOpponent())
                         || game.getPhaseHandler().getPhase().isAfter(PhaseType.COMBAT_DECLARE_ATTACKERS))) {
                return AiPlayDecision.AnotherTime;
            }

            // Prevent the computer from summoning Ball Lightning type creatures after attacking
            if (card.hasStartOfKeyword("You may cast CARDNAME as though it had flash. If") && !card.getController().couldCastSorcery(sa)) {
                return AiPlayDecision.AnotherTime;
            }
            
            // Wait for Main2 if possible
            if (game.getPhaseHandler().is(PhaseType.MAIN1)
                    && game.getPhaseHandler().isPlayerTurn(player)
                    && player.getManaPool().totalMana() <= 0
                    && !ComputerUtil.castPermanentInMain1(player, sa)) {
                return AiPlayDecision.WaitForMain2;
            }
            // save cards with flash for surprise blocking
            if (card.hasKeyword("Flash")
                    && (player.isUnlimitedHandSize() || player.getCardsIn(ZoneType.Hand).size() <= player.getMaxHandSize())
                    && player.getManaPool().totalMana() <= 0
                    && (game.getPhaseHandler().isPlayerTurn(player)
                            || game.getPhaseHandler().getPhase().isBefore(PhaseType.COMBAT_DECLARE_ATTACKERS)
                    && !card.hasETBTrigger())
                    && !ComputerUtil.castPermanentInMain1(player, sa)) {
                return AiPlayDecision.AnotherTime;
            }

            return canPlayFromEffectAI((SpellPermanent)sa, false, true);
        } else if( sa instanceof Spell ) {
            return canPlaySpellBasic(card);
        }
        return AiPlayDecision.WillPlay;
    }

    private AiPlayDecision canPlaySpellBasic(final Card card) {
        if (card.getSVar("NeedsToPlay").length() > 0) {
            final String needsToPlay = card.getSVar("NeedsToPlay");
            List<Card> list = game.getCardsIn(ZoneType.Battlefield);

            list = CardLists.getValidCards(list, needsToPlay.split(","), card.getController(), card);
            if (list.isEmpty()) {
                return AiPlayDecision.MissingNeededCards;
            }
        }
        if (card.getSVar("NeedsToPlayVar").length() > 0) {
            final String needsToPlay = card.getSVar("NeedsToPlayVar");
            int x = 0;
            int y = 0;
            String sVar = needsToPlay.split(" ")[0];
            String comparator = needsToPlay.split(" ")[1];
            String compareTo = comparator.substring(2);
            try {
                x = Integer.parseInt(sVar);
            } catch (final NumberFormatException e) {
                x = CardFactoryUtil.xCount(card, card.getSVar(sVar));
            }
            try {
                y = Integer.parseInt(compareTo);
            } catch (final NumberFormatException e) {
                y = CardFactoryUtil.xCount(card, card.getSVar(compareTo));
            }
            if (!Expressions.compare(x, comparator, y)) {
                return AiPlayDecision.NeedsToPlayCriteriaNotMet;
            }
        }
        return AiPlayDecision.WillPlay;
    }
    
    // not sure "playing biggest spell" matters?
    private final static Comparator<SpellAbility> saComparator = new Comparator<SpellAbility>() {
        @Override
        public int compare(final SpellAbility a, final SpellAbility b) {
            // sort from highest cost to lowest
            // we want the highest costs first
            int a1 = a.getPayCosts() == null ? 0 : a.getPayCosts().getTotalMana().getCMC();
            int b1 = b.getPayCosts() == null ? 0 : b.getPayCosts().getTotalMana().getCMC();

            // deprioritize planar die roll marked with AIRollPlanarDieParams:LowPriority$ True
            if (ApiType.RollPlanarDice == a.getApi() && a.getHostCard().hasSVar("AIRollPlanarDieParams") && a.getHostCard().getSVar("AIRollPlanarDieParams").toLowerCase().matches(".*lowpriority\\$\\s*true.*")) {
                return 1;
            } else if (ApiType.RollPlanarDice == b.getApi() && b.getHostCard().hasSVar("AIRollPlanarDieParams") && b.getHostCard().getSVar("AIRollPlanarDieParams").toLowerCase().matches(".*lowpriority\\$\\s*true.*")) {
                return -1;
            }
    
            // cast 0 mana cost spells first (might be a Mox)
            if (a1 == 0 && b1 > 0) {
                return -1;
            } else if (a1 > 0 && b1 == 0) {
                return 1;
            }

            a1 += getSpellAbilityPriority(a);
            b1 += getSpellAbilityPriority(b);

            return b1 - a1;
        }
        
        private int getSpellAbilityPriority(SpellAbility sa) {
            int p = 0;
            Card source = sa.getHostCard();
            // puts creatures in front of spells
            if (source.isCreature()) {
                p += 1;
            }
            // don't play equipments before having any creatures
            if (source.isEquipment() && sa.getHostCard().getController().getCreaturesInPlay().isEmpty()) {
                p -= 9;
            }
            // artifacts and enchantments with effects that do not stack
            if ("True".equals(source.getSVar("NonStackingEffect")) && source.getController().isCardInPlay(source.getName())) {
                p -= 9;
            }
            // sort planeswalker abilities for ultimate
            if (sa.getRestrictions().getPlaneswalker()) {
                if (sa.hasParam("Ultimate")) {
                    p += 9;
                }
            }
    
            if (ApiType.DestroyAll == sa.getApi()) {
                p += 4;
            }

            return p;
        }
    }; // Comparator
    
    /**
     * <p>
     * AI_discardNumType.
     * </p>
     * 
     * @param numDiscard
     *            a int.
     * @param uTypes
     *            an array of {@link java.lang.String} objects. May be null for
     *            no restrictions.
     * @param sa
     *            a {@link forge.game.spellability.SpellAbility} object.
     * @return a List<Card> of discarded cards.
     */
    public List<Card> getCardsToDiscard(final int numDiscard, final String[] uTypes, final SpellAbility sa) {
        List<Card> hand = new ArrayList<Card>(player.getCardsIn(ZoneType.Hand));

    
        if ((uTypes != null) && (sa != null)) {
            hand = CardLists.getValidCards(hand, uTypes, sa.getActivatingPlayer(), sa.getHostCard());
        }
        return getCardsToDiscard(numDiscard, numDiscard, hand, sa);
    }
    
    public List<Card> getCardsToDiscard(final int min, final int max, final List<Card> validCards, final SpellAbility sa) {
        
        if (validCards.size() < min) {
            return null;
        }

        Card sourceCard = null;
        final List<Card> discardList = new ArrayList<Card>();
        int count = 0;
        if (sa != null) {
            sourceCard = sa.getHostCard();
        }
    
        // look for good discards
        while (count < min) {
            Card prefCard = null;
            if (sa != null && sa.getActivatingPlayer() != null && sa.getActivatingPlayer().isOpponentOf(player)) {
                for (Card c : validCards) {
                    if (c.hasKeyword("If a spell or ability an opponent controls causes you to discard CARDNAME,"
                            + " put it onto the battlefield instead of putting it into your graveyard.")
                            || !c.getSVar("DiscardMeByOpp").isEmpty()) {
                        prefCard = c;
                        break;
                    }
                }
            }
            if (prefCard == null) {
                prefCard = ComputerUtil.getCardPreference(player, sourceCard, "DiscardCost", validCards);
            }
            if (prefCard != null) {
                discardList.add(prefCard);
                validCards.remove(prefCard);
                count++;
            } else {
                break;
            }
        }
    
        final int discardsLeft = min - count;
    
        // choose rest
        for (int i = 0; i < discardsLeft; i++) {
            if (validCards.isEmpty()) {
                continue;
            }
            final int numLandsInPlay = Iterables.size(Iterables.filter(player.getCardsIn(ZoneType.Battlefield), CardPredicates.Presets.LANDS));
            final List<Card> landsInHand = CardLists.filter(validCards, CardPredicates.Presets.LANDS);
            final int numLandsInHand = landsInHand.size();
    
            // Discard a land
            boolean canDiscardLands = numLandsInHand > 3  || (numLandsInHand > 2 && numLandsInPlay > 0)
            || (numLandsInHand > 1 && numLandsInPlay > 2) || (numLandsInHand > 0 && numLandsInPlay > 5);
    
            if (canDiscardLands) {
                discardList.add(landsInHand.get(0));
                validCards.remove(landsInHand.get(0));
            } else { // Discard other stuff
                CardLists.sortByCmcDesc(validCards);
                int numLandsAvailable = numLandsInPlay;
                if (numLandsInHand > 0) {
                    numLandsAvailable++;
                }
                //Discard unplayable card
                if (validCards.get(0).getCMC() > numLandsAvailable) {
                    discardList.add(validCards.get(0));
                    validCards.remove(validCards.get(0));
                } else { //Discard worst card
                    Card worst = ComputerUtilCard.getWorstAI(validCards);
                    discardList.add(worst);
                    validCards.remove(worst);
                }
            }
        }
    
        return discardList;
    }

    @SuppressWarnings("incomplete-switch")
    public boolean confirmAction(SpellAbility sa, PlayerActionConfirmMode mode, String message) {
        ApiType api = sa.getApi();

        // Abilities without api may also use this routine, However they should provide a unique mode value
        if ( null == api ) {
            if( mode != null ) switch (mode) {
                // case BraidOfFire: return true;
                // case Ripple: return true;
            }

            String exMsg = String.format("AI confirmAction does not know what to decide about %s mode (api is null).", mode);
            throw new IllegalArgumentException(exMsg);

        } else 
            return SpellApiToAi.Converter.get(api).confirmAction(player, sa, mode, message);
    }

    public boolean confirmBidAction(SpellAbility sa, PlayerActionConfirmMode mode, String message,
            int bid, Player winner) {
        if (mode != null) switch (mode) {
            case BidLife:
                if (sa.hasParam("AIBidMax")) {
                    return !player.equals(winner) && bid < Integer.parseInt(sa.getParam("AIBidMax")) && player.getLife() > bid + 5;
                } else {
                    return false;
                }
            default:
                return false;
        } 
        return false;
    }
    

    public boolean confirmStaticApplication(Card hostCard, GameEntity affected, String logic, String message) {
        if (logic.equalsIgnoreCase("ProtectFriendly")) {
            final Player controller = hostCard.getController();
            if (affected instanceof Player) {
                return !((Player) affected).isOpponentOf(controller);
            } else if (affected instanceof Card) {
                return !((Card) affected).getController().isOpponentOf(controller);
            }
        }
        return true;
    }

    public String getProperty(AiProps propName) {
        return AiProfileUtil.getAIProp(getPlayer().getLobbyPlayer(), propName);
    }

    public int getIntProperty(AiProps propName) {
        return Integer.parseInt(AiProfileUtil.getAIProp(getPlayer().getLobbyPlayer(), propName));
    }

    public boolean getBooleanProperty(AiProps propName) {
        return Boolean.parseBoolean(AiProfileUtil.getAIProp(getPlayer().getLobbyPlayer(), propName));
    }

    /** Returns the spell ability which has already been played - use it for reference only */ 
    public SpellAbility chooseAndPlaySa(boolean mandatory, boolean withoutPayingManaCost, final SpellAbility... list) {
        return chooseAndPlaySa(Arrays.asList(list), mandatory, withoutPayingManaCost);
    }
    /** Returns the spell ability which has already been played - use it for reference only */
    public SpellAbility chooseAndPlaySa(final List<SpellAbility> choices, boolean mandatory, boolean withoutPayingManaCost) {
        for (final SpellAbility sa : choices) {
            sa.setActivatingPlayer(player);
            //Spells
            if (sa instanceof Spell) {
                if (AiPlayDecision.WillPlay != canPlayFromEffectAI((Spell) sa, mandatory, withoutPayingManaCost)) {
                    continue;
                }
            } else {
                if (AiPlayDecision.WillPlay == canPlaySa(sa)) {
                    continue;
                }
            }
            
            if ( withoutPayingManaCost )
                ComputerUtil.playSpellAbilityWithoutPayingManaCost(player, sa, game);
            else if (!ComputerUtilCost.canPayCost(sa, player)) 
                continue;
            else
                ComputerUtil.playStack(sa, player, game);
            return sa;
        }
        return null;
    }
    
    public AiPlayDecision canPlayFromEffectAI(Spell spell, boolean mandatory, boolean withoutPayingManaCost) {
        
        final Card card = spell.getHostCard();
        if( spell instanceof SpellApiBased )
        {
            boolean chance = false;
            if (withoutPayingManaCost) {
                chance = SpellApiToAi.Converter.get(spell.getApi()).doTriggerNoCostWithSubs(player, spell, mandatory);
            } else {
                chance = SpellApiToAi.Converter.get(spell.getApi()).doTriggerAI(player, spell, mandatory);
            }
            if (!chance)
                return AiPlayDecision.TargetingFailed;

            return canPlaySpellBasic(card);
        }
        
        if ( spell instanceof SpellPermanent) {
            if (mandatory) {
                return AiPlayDecision.WillPlay;
            }
            ManaCost mana = spell.getPayCosts().getTotalMana();
            final Cost cost = spell.getPayCosts();
    
            if (cost != null) {
                // AI currently disabled for these costs
                if (!ComputerUtilCost.checkLifeCost(player, cost, card, 4, null)) {
                    return AiPlayDecision.CostNotAcceptable;
                }
    
                if (!ComputerUtilCost.checkDiscardCost(player, cost, card)) {
                    return AiPlayDecision.CostNotAcceptable;
                }
    
                if (!ComputerUtilCost.checkSacrificeCost(player, cost, card)) {
                    return AiPlayDecision.CostNotAcceptable;
                }
    
                if (!ComputerUtilCost.checkRemoveCounterCost(cost, card)) {
                    return AiPlayDecision.CostNotAcceptable;
                }
            }
    
            // check on legendary
            if (card.isType("Legendary")
                    && !game.getStaticEffects().getGlobalRuleChange(GlobalRuleChange.noLegendRule)) {
                final List<Card> list = player.getCardsIn(ZoneType.Battlefield);
                if (Iterables.any(list, CardPredicates.nameEquals(card.getName()))) {
                    return AiPlayDecision.WouldDestroyLegend;
                }
            }
            if (card.isPlaneswalker()) {
                List<Card> list = player.getCardsIn(ZoneType.Battlefield);
                list = CardLists.filter(list, CardPredicates.Presets.PLANEWALKERS);

                List<String> type = card.getType();
                final String subtype = type.get(type.size() - 1);
                final List<Card> cl = CardLists.getType(list, subtype);
                if (!cl.isEmpty()) {
                    return AiPlayDecision.WouldDestroyOtherPlaneswalker;
                }
            }
            if (card.isType("World")) {
                List<Card> list = player.getCardsIn(ZoneType.Battlefield);
                list = CardLists.getType(list, "World");
                if (!list.isEmpty()) {
                    return AiPlayDecision.WouldDestroyWorldEnchantment;
                }
            }
    
            if (card.isCreature() && (card.getNetDefense() <= 0) && !card.hasStartOfKeyword("etbCounter")
                    && mana.countX() == 0 && !card.hasETBTrigger()
                    && !card.hasETBReplacement()) {
                return AiPlayDecision.WouldBecomeZeroToughnessCreature;
            }
    
            if (!checkETBEffects(card, spell, null)) {
                return AiPlayDecision.BadEtbEffects;
            }
            if (ComputerUtil.damageFromETB(player, card) >= player.getLife() && player.canLoseLife()) {
                return AiPlayDecision.BadEtbEffects;
            }
        }
        return canPlaySpellBasic(card);
    }

    public SpellAbility choooseSpellAbilityToPlay() {
        final PhaseType phase = game.getPhaseHandler().getPhase();

        if (game.getStack().isEmpty() && phase.isMain()) {
            Log.debug("Computer " + phase.nameForUi);
            List<Card> landsWannaPlay = getLandsToPlay();
            if(landsWannaPlay != null && !landsWannaPlay.isEmpty() && player.canPlayLand(null)) {
                Card land = chooseBestLandToPlay(landsWannaPlay);
                if (ComputerUtil.damageFromETB(player, land) < player.getLife() || !player.canLoseLife()) {
                    Ability.PLAY_LAND_SURROGATE.setHostCard(land);
                    return Ability.PLAY_LAND_SURROGATE;
                }
            }
        }

        SpellAbility sa = getSpellAbilityToPlay();
        // System.out.println("Chosen to play: " + sa);
        return sa;
    }

    // declares blockers for given defender in a given combat
    public void declareBlockersFor(Player defender, Combat combat) {
        AiBlockController block = new AiBlockController(defender);
        // When player != defender, AI should declare blockers for its benefit.
        block.assignBlockers(combat);
    }
    

    public Combat declareAttackers(Player attacker, Combat combat) {
        // 12/2/10(sol) the decision making here has moved to getAttackers()
        AiAttackController aiAtk = new AiAttackController(attacker); 
        aiAtk.declareAttackers(combat);

        for (final Card element : combat.getAttackers()) {
            // tapping of attackers happens after Propaganda is paid for
            final StringBuilder sb = new StringBuilder();
            sb.append("Computer just assigned ").append(element.getName()).append(" as an attacker.");
            Log.debug(sb.toString());
        }

        // ai is about to attack, cancel all phase skipping
        for (Player p : game.getPlayers()) {
            p.getController().autoPassCancel();
        }
        return combat;
    }

    private final SpellAbility getSpellAbilityToPlay() {
        // if top of stack is owned by me
        if (!game.getStack().isEmpty() && game.getStack().peekAbility().getActivatingPlayer().equals(player)) {
            // probably should let my stuff resolve
            return null;
        }
        final List<Card> cards = getAvailableCards();
    
        if ( !game.getStack().isEmpty() ) {
            SpellAbility counter = chooseCounterSpell(getPlayableCounters(cards));
            if( counter != null ) return counter;
    
            SpellAbility counterETB = chooseSpellAbilyToPlay(this.getPossibleETBCounters(), false);
            if( counterETB != null )
                return counterETB;
        }
    
        SpellAbility result = chooseSpellAbilyToPlay(getSpellAbilities(cards), true);
        if( null == result) 
            return null;
        return result;
    }
    
    private SpellAbility chooseSpellAbilyToPlay(final ArrayList<SpellAbility> all, boolean skipCounter) {
        if ( all == null || all.isEmpty() )
            return null;

        Collections.sort(all, saComparator); // put best spells first
        
        for (final SpellAbility sa : getOriginalAndAltCostAbilities(all)) {
            // Don't add Counterspells to the "normal" playcard lookups
            if (sa.getApi() == ApiType.Counter && skipCounter) {
                continue;
            }
            sa.setActivatingPlayer(player);
            
            AiPlayDecision opinion = canPlayAndPayFor(sa);
            // PhaseHandler ph = game.getPhaseHandler();
            // System.out.printf("Ai thinks '%s' of %s -> %s @ %s %s >>> \n", opinion, sa.getHostCard(), sa, Lang.getPossesive(ph.getPlayerTurn().getName()), ph.getPhase());
            
            if (opinion != AiPlayDecision.WillPlay)
                continue;
    
            return sa;
        }
        
        return null;
    }     

    public List<Card> chooseCardsToDelve(int colorlessCost, List<Card> grave) {
        List<Card> toExile = new ArrayList<Card>();
        int numToExile = Math.min(grave.size(), colorlessCost);
        
        for (int i = 0; i < numToExile; i++) {
            Card chosen = null;
            for (final Card c : grave) { // Exile noncreatures first in
                // case we can revive. Might
                // wanna do some additional
                // checking here for Flashback
                // and the like.
                if (!c.isCreature()) {
                    chosen = c;
                    break;
                }
            }
            if (chosen == null) {
                chosen = ComputerUtilCard.getWorstCreatureAI(grave);
            }

            if (chosen == null) {
                // Should never get here but... You know how it is.
                chosen = grave.get(0);
            }

            toExile.add(chosen);
            grave.remove(chosen);
        }
        return toExile;
    }
    
    public boolean doTrigger(SpellAbility spell, boolean mandatory) {
        
        if ( spell.getApi() != null )
            return SpellApiToAi.Converter.get(spell.getApi()).doTriggerAI(player, spell, mandatory);
        if ( spell instanceof WrappedAbility )
            return doTrigger(((WrappedAbility)spell).getWrappedAbility(), mandatory);
        
        return false;
    }
    
    /**
     * Ai should run.
     *
     * @param sa the sa
     * @param ai 
     * @return true, if successful
     */
    public final boolean aiShouldRun(final ReplacementEffect effect, final SpellAbility sa) {
        Card hostCard = effect.getHostCard();
        if (effect.getMapParams().containsKey("AICheckSVar")) {
            System.out.println("aiShouldRun?" + sa);
            final String svarToCheck = effect.getMapParams().get("AICheckSVar");
            String comparator = "GE";
            int compareTo = 1;

            if (effect.getMapParams().containsKey("AISVarCompare")) {
                final String fullCmp = effect.getMapParams().get("AISVarCompare");
                comparator = fullCmp.substring(0, 2);
                final String strCmpTo = fullCmp.substring(2);
                try {
                    compareTo = Integer.parseInt(strCmpTo);
                } catch (final Exception ignored) {
                    if (sa == null) {
                        compareTo = CardFactoryUtil.xCount(hostCard, hostCard.getSVar(strCmpTo));
                    } else {
                        compareTo = AbilityUtils.calculateAmount(hostCard, hostCard.getSVar(strCmpTo), sa);
                    }
                }
            }

            int left = 0;

            if (sa == null) {
                left = CardFactoryUtil.xCount(hostCard, hostCard.getSVar(svarToCheck));
            } else {
                left = AbilityUtils.calculateAmount(hostCard, svarToCheck, sa);
            }
            System.out.println("aiShouldRun?" + left + comparator + compareTo);
            if (Expressions.compare(left, comparator, compareTo)) {
                return true;
            }
        } else if (effect.getMapParams().containsKey("AICheckDredge")) {
            return player.getCardsIn(ZoneType.Library).size() > 8 || player.isCardInPlay("Laboratory Maniac");
        } else if (sa != null && doTrigger(sa, false)) {
            return true;
        }

        return false;
    }    
    
    
    public List<SpellAbility> chooseSaToActivateFromOpeningHand(List<SpellAbility> usableFromOpeningHand) {
        // AI would play everything. But limits to one copy of (Leyline of Singularity) and (Gemstone Caverns)
        
        List<SpellAbility> result = new ArrayList<SpellAbility>();
        for(SpellAbility sa : usableFromOpeningHand) {
            // Is there a better way for the AI to decide this?
            if (doTrigger(sa, false)) {
                result.add(sa);
            }
        }
        
        boolean hasLeyline1 = false;
        SpellAbility saGemstones = null;
        
        for(int i = 0; i < result.size(); i++) {
            SpellAbility sa = result.get(i);
            
            String srcName = sa.getHostCard().getName();
            if("Gemstone Caverns".equals(srcName)) {
                if(saGemstones == null)
                    saGemstones = sa;
                else
                    result.remove(i--);
            } else if ("Leyline of Singularity".equals(srcName)) {
                if(!hasLeyline1)
                    hasLeyline1 = true;
                else
                    result.remove(i--);
            }
        }
        
        // Play them last
        if( saGemstones != null ) {
            result.remove(saGemstones);
            result.add(saGemstones);
        }
        
        return result;
    }

    public int chooseNumber(SpellAbility sa, String title, int min, int max) {
        final String logic = sa.getParam("AILogic");
        if ("GainLife".equals(logic)) {
            if (player.getLife() < 5 || player.getCardsIn(ZoneType.Hand).size() >= player.getMaxHandSize()) {
                return min;
            }
        } else if ("LoseLife".equals(logic)) {
            if (player.getLife() > 5) {
                return min;
            }
        } else if ("Min".equals(logic)) {
            return min;
        } else if ("DigACard".equals(logic)) {
            int random = MyRandom.getRandom().nextInt(Math.min(4, max)) + 1;
            if (player.getLife() < random + 5) {
                return min;
            } else {
                return random;
            }
        } else if ("Damnation".equals(logic)) {
            int chosenMax = player.getLife() - 1;
            int cardsInPlay = player.getCardsIn(ZoneType.Battlefield).size();
            return Math.min(chosenMax, cardsInPlay);
        } else if ("OptionalDraw".equals(logic)) {
            int cardsInHand = player.getCardsIn(ZoneType.Hand).size();
            int maxDraw = Math.min(player.getMaxHandSize() + 2 - cardsInHand, max);
            int maxCheckLib = Math.min(maxDraw, player.getCardsIn(ZoneType.Library).size());
            return Math.max(min, maxCheckLib);
        } else if ("RepeatDraw".equals(logic)) {
            int remaining = player.getMaxHandSize() - player.getCardsIn(ZoneType.Hand).size()
                    + MyRandom.getRandom().nextInt(3);
            return Math.max(remaining, min) / 2;
        } else if ("LowestLoseLife".equals(logic)) {
            return MyRandom.getRandom().nextInt(Math.min(player.getLife() / 3, player.getOpponent().getLife())) + 1;
        } else if ("HighestGetCounter".equals(logic)) {
            return MyRandom.getRandom().nextInt(3);
        }
        return max;
    }

    public boolean confirmPayment(CostPart costPart) {
        throw new UnsupportedOperationException("AI is not supposed to reach this code at the moment");
    }

    public Map<GameEntity, CounterType> chooseProliferation() {
        final Map<GameEntity, CounterType> result = new HashMap<>();  
        
        final List<Player> allies = player.getAllies();
        allies.add(player);
        final List<Player> enemies = player.getOpponents();
        final Function<Card, CounterType> predProliferate = new Function<Card, CounterType>() {
            @Override
            public CounterType apply(Card crd) {
                for (final Entry<CounterType, Integer> c1 : crd.getCounters().entrySet()) {
                    if (ComputerUtil.isNegativeCounter(c1.getKey(), crd) && enemies.contains(crd.getController())) {
                        return c1.getKey();
                    }
                    if (!ComputerUtil.isNegativeCounter(c1.getKey(), crd) && allies.contains(crd.getController())) {
                        return c1.getKey();
                    }
                }
                return null;
            }
        };

        for (Card c : game.getCardsIn(ZoneType.Battlefield)) {
            CounterType ct = predProliferate.apply(c);
            if( ct != null)
                result.put(c, ct);
        }
        
        for (Player e : enemies) {
            if (e.getPoisonCounters() > 0) {
                result.put(e, null); // poison counter type is hardcoded at data consumer's side (this works while players may have no other counters)
            }
        }

        return result;
    }

    public List<Card> chooseCardsForEffect(List<Card> pool, SpellAbility sa, int min, int max, boolean isOptional) {
        if( sa == null || sa.getApi() == null ) {
            throw new UnsupportedOperationException();
        }
        List<Card> result = new ArrayList<>();
        switch( sa.getApi()) {
            case TwoPiles:
                // TODO: improve AI
                Card biggest = null;
                Card smallest = null;
                biggest = pool.get(0);
                smallest = pool.get(0);

                for (Card c : pool) {
                    if (c.getCMC() >= biggest.getCMC()) {
                        biggest = c;
                    } else if (c.getCMC() <= smallest.getCMC()) {
                        smallest = c;
                    }
                }
                result.add(biggest);

                if (max > 3 && !result.contains(smallest)) {
                    result.add(smallest);
                }
                break;
            case MultiplePiles:
                // Whims of the Fates {all, 0, 0}
                result.addAll(pool);
                break;
            default:
                for (int i = 0; i < max; i++) {
                    Card c = player.getController().chooseSingleEntityForEffect(pool, sa, null, isOptional);
                    if (c != null) {
                        result.add(c);
                        pool.remove(c);
                    } else {
                        break;
                    }
                }
                
        
        }
        return result;
        
    }

    public Collection<? extends PaperCard> complainCardsCantPlayWell(Deck myDeck) {
        List<PaperCard> result = new ArrayList<PaperCard>();
        for ( Entry<DeckSection, CardPool> ds : myDeck ) {
            for (Entry<PaperCard, Integer> cp : ds.getValue()) {
                if ( cp.getKey().getRules().getAiHints().getRemAIDecks() ) 
                    result.add(cp.getKey());
            }
        }
        return result;
    }


    // this is where the computer cheats
    // changes AllZone.getComputerPlayer().getZone(Zone.Library)
    
    /**
     * <p>
     * smoothComputerManaCurve.
     * </p>
     * 
     * @param in
     *            an array of {@link forge.game.card.Card} objects.
     * @return an array of {@link forge.game.card.Card} objects.
     */
    public List<Card> cheatShuffle(List<Card> in) {
        if( in.size() < 20 || !canCheatShuffle() )
            return in;
        
        final List<Card> library = Lists.newArrayList(in);
        CardLists.shuffle(library);
    
        // remove all land, keep non-basicland in there, shuffled
        List<Card> land = CardLists.filter(library, CardPredicates.Presets.LANDS);
        for (Card c : land) {
            if (c.isLand()) {
                library.remove(c);
            }
        }
    
        try {
            // mana weave, total of 7 land
            // The Following have all been reduced by 1, to account for the
            // computer starting first.
            library.add(5, land.get(0));
            library.add(6, land.get(1));
            library.add(8, land.get(2));
            library.add(9, land.get(3));
            library.add(10, land.get(4));
    
            library.add(12, land.get(5));
            library.add(15, land.get(6));
        } catch (final IndexOutOfBoundsException e) {
            System.err.println("Error: cannot smooth mana curve, not enough land");
            return in;
        }
    
        // add the rest of land to the end of the deck
        for (int i = 0; i < land.size(); i++) {
            if (!library.contains(land.get(i))) {
                library.add(land.get(i));
            }
        }
    
        // check
        for (int i = 0; i < library.size(); i++) {
            System.out.println(library.get(i));
        }
    
        return library;
    } // smoothComputerManaCurve()

    
    public int chooseNumber(SpellAbility sa, String title,List<Integer> options, Player relatedPlayer) {
        switch(sa.getApi())
        {
            case SetLife:
                if (relatedPlayer.equals(sa.getHostCard().getController())) {
                    return Collections.max(options);
                } else if (relatedPlayer.isOpponentOf(sa.getHostCard().getController())) {
                    return Collections.min(options);
                } else {
                    return options.get(0);
                }
            default:
                return 0;
        }
    }


}

