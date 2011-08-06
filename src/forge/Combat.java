package forge;
import java.util.*;

public class Combat
{
  //key is attacker Card
  //value is CardList of blockers
  private Map<Card,CardList> map = new HashMap<Card,CardList>();
  private Set<Card> blocked = new HashSet<Card>();
  
  private HashMap<Card,CardList> unblockedMap = new HashMap<Card,CardList>();

  private int attackingDamage;
  private int defendingDamage;
  private int defendingFirstStrikeDamage;
  //private int trampleDamage;
  //private int trampleFirstStrikeDamage;

  private String attackingPlayer;
  private String defendingPlayer;
  
  private int declaredAttackers;

  private Card planeswalker;

  public Combat() {reset();}

  public void reset()
  {
    planeswalker = null;

    map.clear();
    blocked.clear();
    
    unblockedMap.clear();
    
    attackingDamage = 0;
    defendingDamage = 0;
    defendingFirstStrikeDamage = 0;

    declaredAttackers = 0;
    attackingPlayer = "";
    defendingPlayer = "";
  }
  public void setPlaneswalker(Card c){planeswalker = c;}
  public Card getPlaneswalker() {return planeswalker;}
  
  public int getDeclaredAttackers() { return declaredAttackers;}

  public void setAttackingPlayer(String player) {attackingPlayer = player;}
  public void setDefendingPlayer(String player) {defendingPlayer = player;}

  public String getAttackingPlayer() {return attackingPlayer;}
  public String getDefendingPlayer() {return defendingPlayer;}

      //relates to defending player damage
  public int getDefendingDamage() {return defendingDamage;}
  public int getDefendingFirstStrikeDamage() {return defendingFirstStrikeDamage;}
  public void setDefendingDamage()
  {
        defendingDamage = 0;
        CardList att = new CardList(getAttackers());
        //sum unblocked attackers' power
        for(int i = 0; i < att.size(); i++) {
          if(! isBlocked(att.get(i))) {
           int damageDealt = att.get(i).getNetAttack();
             if (CombatUtil.isDoranInPlay())
                damageDealt = att.get(i).getNetDefense();

             //if the creature has first strike do not do damage in the normal combat phase
             if(att.get(i).hasSecondStrike())
                defendingDamage += damageDealt;
             }
          }
      }
  public void setDefendingFirstStrikeDamage()
  {
        defendingFirstStrikeDamage = 0;
        CardList att = new CardList(getAttackers());
        //sum unblocked attackers' power
        for(int i = 0; i < att.size(); i++) {
          if(! isBlocked(att.get(i))) {
           int damageDealt = att.get(i).getNetAttack();
             if (CombatUtil.isDoranInPlay())
                damageDealt = att.get(i).getNetDefense();
             
             //if the creature has first strike or double strike do damage in the first strike combat phase
  
             if(att.get(i).hasFirstStrike() || att.get(i).hasDoubleStrike()){
                defendingFirstStrikeDamage += damageDealt;
             }
          }
        }
      }
  public void addDefendingDamage(int n) {defendingDamage += n;}
  public void addDefendingFirstStrikeDamage(int n) {defendingFirstStrikeDamage += n;}
     
  public void addAttackingDamage(int n) {attackingDamage += n;}
  public int getAttackingDamage() {return attackingDamage;}

  public void addAttacker(Card c) {map.put(c, new CardList()); declaredAttackers++;}
  public void resetAttackers()    {map.clear();}
  public Card[] getAttackers()
  {
    CardList out = new CardList();
    Iterator<Card> it = map.keySet().iterator();
    //int i = 0; //unused
    
    while(it.hasNext()) {
      out.add((Card)it.next()); 
    }

    return out.toArray();
  }//getAttackers()

  public boolean isBlocked(Card attacker) {return blocked.contains(attacker);}
  public void addBlocker(Card attacker, Card blocker)
  {
    blocked.add(attacker);
    getList(attacker).add(blocker);
    CombatUtil.checkBlockedAttackers(attacker, blocker);
  }
  public void resetBlockers()
  {
    reset();

    CardList att = new CardList(getAttackers());
    for(int i = 0; i < att.size(); i++)
      addAttacker(att.get(i));
  }
  public CardList getAllBlockers()
  {
    CardList att = new CardList(getAttackers());
    CardList block = new CardList();

    for(int i = 0; i < att.size(); i++)
      block.addAll(getBlockers(att.get(i)).toArray());

    return block;
  }//getAllBlockers()

  public  CardList getBlockers(Card attacker) {return new CardList(getList(attacker).toArray());
	  }
  private CardList getList(Card attacker)     {return (CardList)map.get(attacker);}

  public void removeFromCombat(Card c)
  {
    //is card an attacker?
    CardList att = new CardList(getAttackers());
    if(att.contains(c))
      map.remove(c);
    else//card is a blocker
    {
      for(int i = 0; i < att.size(); i++)
        if(getBlockers(att.get(i)).contains(c))
          getList(att.get(i)).remove(c);
    }
  }//removeFromCombat()
  public void verifyCreaturesInPlay()
  {
    CardList all = new CardList();
    all.addAll(getAttackers());
    all.addAll(getAllBlockers().toArray());

    for(int i = 0; i < all.size(); i++)
      if(! AllZone.GameAction.isCardInPlay(all.get(i)))
        removeFromCombat(all.get(i));
  }//verifyCreaturesInPlay()

      //set Card.setAssignedDamage() for all creatures in combat
      //also assigns player damage by setPlayerDamage()
      public void setAssignedFirstStrikeDamage()
      {
       setDefendingFirstStrikeDamage();
       
        CardList block;
        CardList attacking = new CardList(getAttackers());
        for(int i = 0; i < attacking.size(); i++)
        {
         
             block = getBlockers(attacking.get(i));
       
             //attacker always gets all blockers' attack
             AllZone.GameAction.setAssignedDamage(attacking.get(i), block, CardListUtil.sumFirstStrikeAttack(block));
             //attacking.get(i).setAssignedDamage(CardListUtil.sumFirstStrikeAttack(block));
             if(block.size() == 0)//this damage is assigned to a player by setPlayerDamage()
             {
                //GameActionUtil.executePlayerCombatDamageEffects(attacking.get(i));
                addUnblockedAttacker(attacking.get(i));
             }
             else if(block.size() == 1)
             {
              if(attacking.get(i).hasFirstStrike() || attacking.get(i).hasDoubleStrike()){
                 int damageDealt = attacking.get(i).getNetAttack();
                 if (CombatUtil.isDoranInPlay())
                    damageDealt = attacking.get(i).getNetDefense();
                    
                 //block.get(0).setAssignedDamage(damageDealt);
                 CardList cl = new CardList();
                 cl.add(attacking.get(i));
                 AllZone.GameAction.setAssignedDamage(block.get(0), cl , damageDealt);
                 
                 //trample
                 int trample = damageDealt - block.get(0).getNetDefense();
                 if(attacking.get(i).getKeyword().contains("Trample") && 0 < trample)
                 {
                    this.addDefendingFirstStrikeDamage(trample);
                    //System.out.println("First Strike trample damage: " + trample);
                 }
                }
             }//1 blocker
             else if(getAttackingPlayer().equals(Constant.Player.Computer))
             {
                if(attacking.get(i).hasFirstStrike() || attacking.get(i).hasDoubleStrike()){
                 int damageDealt = attacking.get(i).getNetAttack();
                   if (CombatUtil.isDoranInPlay())
                      damageDealt = attacking.get(i).getNetDefense();
                  setAssignedFirstStrikeDamage(attacking.get(i), block, damageDealt);
                }
             }
             else//human
             {
                if(attacking.get(i).hasFirstStrike() || attacking.get(i).hasDoubleStrike()){
                  //GuiDisplay2 gui = (GuiDisplay2) AllZone.Display;
                 int damageDealt = attacking.get(i).getNetAttack();
                   if (CombatUtil.isDoranInPlay())
                      damageDealt = attacking.get(i).getNetDefense();
                  AllZone.Display.assignDamage(attacking.get(i),block, damageDealt);
                  //System.out.println("setAssignedFirstStrikeDmg called for:" + damageDealt + " damage.");
                }
             }
        }//for

        //should first strike affect the following?
        if(getPlaneswalker() != null)
        {
          //System.out.println("defendingDmg (setAssignedFirstStrikeDamage) :" +defendingFirstStrikeDamage);
          planeswalker.setAssignedDamage(defendingFirstStrikeDamage);
          defendingFirstStrikeDamage = 0;
        }
      }//setAssignedFirstStrikeDamage()
      private void setAssignedFirstStrikeDamage(Card attacker, CardList list, int damage)
      {
        CardListUtil.sortAttack(list);
        Card c;
        for(int i = 0; i < list.size(); i++)
        {
          c = list.get(i);
          if(c.getKillDamage() <= damage)
          {
               damage -= c.getKillDamage();
               CardList cl = new CardList();
               cl.add(attacker);
               
               AllZone.GameAction.setAssignedDamage(c, cl, c.getKillDamage());
               //c.setAssignedDamage(c.getKillDamage());
          }
        }//for
      }//setAssignedFirstStrikeDamage()

      //set Card.setAssignedDamage() for all creatures in combat
      //also assigns player damage by setPlayerDamage()
      public void setAssignedDamage()
      {
        setDefendingDamage();
       
        CardList block;
        CardList attacking = new CardList(getAttackers());
        for(int i = 0; i < attacking.size(); i++)
        {
          if(!attacking.get(i).hasSecondStrike() ){
             block = getBlockers(attacking.get(i));
             
             //attacker always gets all blockers' attack
             //attacking.get(i).setAssignedDamage(CardListUtil.sumAttack(block));
             AllZone.GameAction.setAssignedDamage(attacking.get(i), block, CardListUtil.sumAttack(block));
             if(block.size() == 0)//this damage is assigned to a player by setPlayerDamage()
             {
                //GameActionUtil.executePlayerCombatDamageEffects(attacking.get(i));
                addUnblockedAttacker(attacking.get(i));
             }
             else if(block.size() == 1)
             {
              int damageDealt = attacking.get(i).getNetAttack();
              if (CombatUtil.isDoranInPlay())
                 damageDealt = attacking.get(i).getNetDefense();
                 
               //block.get(0).setAssignedDamage(damageDealt);
              CardList cl = new CardList();
              cl.add(attacking.get(i));
              AllZone.GameAction.setAssignedDamage(block.get(0), cl , damageDealt);
              
              
               //trample
               int trample = damageDealt - block.get(0).getNetDefense();
               if(attacking.get(i).getKeyword().contains("Trample") && 0 < trample) {
                 this.addDefendingDamage(trample);
                 //System.out.println("Reg trample damage: " + trample);
               }
             }//1 blocker
             else if(getAttackingPlayer().equals(Constant.Player.Computer))
             {
              int damageDealt = attacking.get(i).getNetAttack();
                if (CombatUtil.isDoranInPlay())
                   damageDealt = attacking.get(i).getNetDefense();
               setAssignedDamage(attacking.get(i),block, damageDealt);
              
             }
             else//human
             {
               //GuiDisplay2 gui = (GuiDisplay2) AllZone.Display;
              int damageDealt = attacking.get(i).getNetAttack();
              if (CombatUtil.isDoranInPlay())
                   damageDealt = attacking.get(i).getNetDefense();
               AllZone.Display.assignDamage(attacking.get(i), block, damageDealt);
               //System.out.println("setAssignedDmg called for:" + damageDealt + " damage.");
             }
          }//if !hasFirstStrike ...
          //hacky code, to ensure surviving non-first-strike blockers will hit first strike attackers:
          else {
        	  block = getBlockers(attacking.get(i));
        	  //System.out.println("block size: " + block.size());
        	  if( (attacking.get(i).hasFirstStrike() || attacking.get(i).hasDoubleStrike()) )
        	  {
        		  int blockerDamage = 0;
        		  for(int j=0; j < block.size(); j++)
        		  {
        			  blockerDamage += block.get(j).getNetAttack();
        		  }
        		  //attacking.get(i).setAssignedDamage(blockerDamage);
        		  AllZone.GameAction.setAssignedDamage(attacking.get(i), block , blockerDamage);
        	  }
          }
        }//for

        //should first strike affect the following?
        if(getPlaneswalker() != null)
        {
          //System.out.println("defendingDmg (setAssignedDamage): " + defendingDamage);
          planeswalker.setAssignedDamage(defendingDamage);
          defendingDamage = 0;
        }
      }//assignDamage()
      private void setAssignedDamage(Card attacker, CardList list, int damage)
      {
        CardListUtil.sortAttack(list);
        Card c;
        for(int i = 0; i < list.size(); i++)
        {
          c = list.get(i);
          //if(!c.hasFirstStrike() || (c.hasFirstStrike() && c.hasDoubleStrike()) ){
             if(c.getKillDamage() <= damage)
             {
               damage -= c.getKillDamage();
               CardList cl = new CardList();
               cl.add(attacker);
               AllZone.GameAction.setAssignedDamage(c, cl, c.getKillDamage());
               //c.setAssignedDamage(c.getKillDamage());
             }
          //}
        }//for
      }//assignDamage()


      public Card[] getUnblockedAttackers()
      {
        CardList out = new CardList();
        Iterator<Card> it = unblockedMap.keySet().iterator();
        while(it.hasNext()){ //only add creatures without firstStrike to this list.
           Card c = (Card)it.next();
           if(!c.hasFirstStrike()){
              out.add(c);
           }
        }
       
        return out.toArray();
      }//getUnblockedAttackers()

      public Card[] getUnblockedFirstStrikeAttackers()
      {
        CardList out = new CardList();
        Iterator<Card> it = unblockedMap.keySet().iterator();
        while(it.hasNext()){ //only add creatures without firstStrike to this list.
           Card c = (Card)it.next();
           if(c.hasFirstStrike() || c.hasDoubleStrike()){
              out.add(c);
           }
        }
       
        return out.toArray();
      }//getUnblockedAttackers()
     
      public void addUnblockedAttacker(Card c)
      {
        unblockedMap.put(c, new CardList());
      }
     
    }//Class Combat


/*
if(cr only has 1 blocker)
  assign damage to attacker and blocker
else attacking play is computer
  attacker.assignDamage(sum blockers' attack)
  Combat.assignDamage(blockers, attack.getAttack())
else //human
  attacker.assignDamage(sum blockers' attack)
  guiDisplay.assignDamage(blockers, attack.getAtack())
*/
