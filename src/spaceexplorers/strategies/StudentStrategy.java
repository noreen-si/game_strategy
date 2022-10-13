//Noreen Si and Diya Saha
//si000023, sahax050
package spaceexplorers.strategies;

import spaceexplorers.publicapi.*;

import java.util.*;

public class StudentStrategy implements IStrategy {

    /**
     * Method where students can observe the state of the system and schedule events to be executed.
     *
     * @param planets          The current state of the system.
     * @param planetOperations Helper methods students can use to interact with the system.
     * @param eventsToExecute  Queue students will add to in order to schedule events.
     */
    @Override
    public void takeTurn(List<IPlanet> planets, IPlanetOperations planetOperations, Queue<IEvent> eventsToExecute) {
        List<IVisiblePlanet> visiblePlanets = new ArrayList<>();
        HashMap<Integer,IVisiblePlanet> visiblePlanetID = new HashMap<>(); //keys = PlanetID, value = Planet
        //Setting Up
        HashMap<IVisiblePlanet, int[]> futurePlanetScores = new HashMap<>(); //future status of all visible planets
        for (IPlanet planet : planets) {
            if (planet instanceof IVisiblePlanet){
                visiblePlanets.add((IVisiblePlanet) planet);
                visiblePlanetID.put(planet.getId(), (IVisiblePlanet) planet);
            }
        }
        //Setting up futurePlanetScores map
        for (IVisiblePlanet planet: visiblePlanets){
            int[] value = futureStateChecker(planet, 5);
            futurePlanetScores.put(planet, value);
        }
        //This is so we can send out some of our population quickly at the start of the game to planets.
        if(isItFirstTurn(visiblePlanets)) {
            for (IVisiblePlanet planet : visiblePlanets) {
                if (planet.getOwner() == Owner.SELF) {
                    if (planet.getTotalPopulation() > 2) {
                        long homePlanetTotalPopulation = planet.getTotalPopulation()/2;
                        Set<IVisiblePlanet> homePlanetNeighbors = getNeighbors(visiblePlanetID, planet);
                        int numNeighbors = homePlanetNeighbors.size();
                        long transferPopulation = (int) Math.floor(homePlanetTotalPopulation / numNeighbors);
                        for (IVisiblePlanet neighbor : homePlanetNeighbors) {
                            eventsToExecute.offer(planetOperations.transferPeople(planet, neighbor, transferPopulation));
                        }
                    }
                }
            }
        } else {
            //Stage 1: Defending Our Planets
            for (IVisiblePlanet planet: visiblePlanetID.values()){
                int[] futureStatus = futureStateChecker(planet, 5);
                int score = futureStatus[0];
                int shuttles_needed = 0;
                if (score == -2 || score == -1){
                    shuttles_needed = futureStatus[1];
                    Set<IVisiblePlanet> neighbors = getNeighbors(visiblePlanetID, planet);
                    IVisiblePlanet strongNeighbor = getStrongestPlanet(neighbors, shuttles_needed);
                    if (strongNeighbor != null){
                        //Since we found the strong planet we want to use, we now need to update its data in the futurePlanetScores map.
                        int[] strongPlanetData = futurePlanetScores.get(strongNeighbor);
                        int strongPlanetScore = strongPlanetData[0];
                        int updatedSparePopulation = strongPlanetData[1] - shuttles_needed + 1;
                        futurePlanetScores.put(strongNeighbor, new int[] {strongPlanetScore, updatedSparePopulation});

                        long transferPopulation = shuttles_needed + 1;
                        eventsToExecute.offer(planetOperations.transferPeople(strongNeighbor, planet, transferPopulation));
                    }
                    //Stage 2: Attacking planets
                } else if (score == 1){ //planet goes from enemy --> neutral (because they are losing population there). We want to secure these planets
                    shuttles_needed = futureStatus[1];
                    Set<IVisiblePlanet> neighbors = getNeighbors(visiblePlanetID, planet);
                    IVisiblePlanet strongNeighbor = getStrongestPlanet(neighbors, shuttles_needed);
                    if (strongNeighbor != null){
                        //Updating the futurePlanetScores map because we will be using spare population from a strong neighbor
                        int[] strongPlanetData = futurePlanetScores.get(strongNeighbor);
                        int strongPlanetScore = strongPlanetData[0];
                        int updatedSparePopulation = strongPlanetData[1] - shuttles_needed;
                        futurePlanetScores.put(strongNeighbor, new int[] {strongPlanetScore, updatedSparePopulation});

                        long transferPopulation = shuttles_needed;
                        eventsToExecute.offer(planetOperations.transferPeople(strongNeighbor, planet, transferPopulation));
                    }
                } else if (score == 0){ //status of the planet hasn't changed.
                    shuttles_needed = futureStatus[1];
                    //If shuttles_needed is negative, this means that it's still an enemy planet.
                    //We need to send the absolute value of shuttles_needed plus one to attack.
                    if (shuttles_needed <= 0){
                        Set<IVisiblePlanet> neighbors = getNeighbors(visiblePlanetID, planet);
                        IVisiblePlanet strongNeighbor = getStrongestPlanet(neighbors, -shuttles_needed + 1);
                        if (strongNeighbor != null){
                            //Updating the futurePlanetScores map because we will be using spare population from a strong neighbor
                            int[] strongPlanetData = futurePlanetScores.get(strongNeighbor);
                            int strongPlanetScore = strongPlanetData[0];
                            int updatedSparePopulation = strongPlanetData[1] + shuttles_needed;
                            futurePlanetScores.put(strongNeighbor, new int[] {strongPlanetScore, updatedSparePopulation});

                            long transferPopulation = -shuttles_needed+1;
                            eventsToExecute.offer(planetOperations.transferPeople(strongNeighbor, planet, transferPopulation));
                        }
                    }
                } else if(score == 3) {
                    //Attacking an UNOCCUPIED planet or a planet that stays neutral.
                    //We'll send a portion of strong planet spare populations to attack this unoccupied planet and secure it.
                    Set<IVisiblePlanet> neighbors = getNeighbors(visiblePlanetID, planet);
                    for(IVisiblePlanet neighboringPlanet : neighbors) {
                        int neighboringPlanetScore = futurePlanetScores.get(neighboringPlanet)[0];
                        if(neighboringPlanet.getOwner() == Owner.SELF && (neighboringPlanetScore == 0 || neighboringPlanetScore == 2)) {
                            long transferPopulation = futurePlanetScores.get(neighboringPlanet)[1]/3;
                            eventsToExecute.offer(planetOperations.transferPeople(neighboringPlanet, planet, transferPopulation));

                            //updating futurePlanetScores map
                            int[] strongPlanetData = futurePlanetScores.get(neighboringPlanet);
                            int strongPlanetScore = strongPlanetData[0];
                            int updatedSparePopulation = strongPlanetData[1] - (int) transferPopulation;
                            futurePlanetScores.put(neighboringPlanet, new int[] {strongPlanetScore, updatedSparePopulation});
                        }
                    }
                }
            }
        }
    }

    public boolean isItFirstTurn(List<IVisiblePlanet> visiblePlanets){
        //This method is for the very beginning of the game when we are trying to spread out people
        // In this case, we'll only have one planet (the home world)
        int friendlyPlanetsCount = 0;
        for(IVisiblePlanet planet : visiblePlanets) {
            if(planet.getOwner() == Owner.SELF) {
                friendlyPlanetsCount += 1;
            }
        }
        if(friendlyPlanetsCount == 1) {
            return true;
        }
        else {
            return false;
        }
    }


    public Set<IVisiblePlanet> getNeighbors(HashMap<Integer,IVisiblePlanet> visiblePlanetID, IVisiblePlanet planet){
        //This method returns a set of the neighbors of a planet.
        Set<IVisiblePlanet> neighbors = new HashSet<>();
        Set<IEdge> edges = planet.getEdges();
        for (IEdge edge : edges) {
            int neighborId = edge.getDestinationPlanetId();
            if(getPlanet(visiblePlanetID, neighborId) != null) {
                IVisiblePlanet neighbor = getPlanet(visiblePlanetID, neighborId);
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    public IVisiblePlanet getPlanet(HashMap<Integer,IVisiblePlanet> visiblePlanetID, int planetID){
        //Helper method to easily get a planet from the HashMap
        if (visiblePlanetID.containsKey(planetID)){
            return visiblePlanetID.get(planetID);
        } else {
            return null;
        }
    }

    public int compareTo(IVisiblePlanet a, IVisiblePlanet b){
        //This compareTo method is called in the quickSort method that assumes we are player one.
        int ans = 0;
        if (a.getP1Population() < b.getP1Population()){
            ans = -1;
        } else if (a.getP1Population() > b.getP1Population()){
            ans = +1;
        } else if (a.getP1Population() == b.getP1Population()){
            ans = 0;
        }
        return ans;
    }
    public int compareToPlayerTwo(IVisiblePlanet a, IVisiblePlanet b) {
        //This compareTo method is called in the quickSortPlayerTwo method, where we are player 2.
        int ans = 0;
        if (a.getP2Population() < b.getP2Population()){
            ans = -1;
        } else if (a.getP2Population() > b.getP2Population()){
            ans = +1;
        } else if (a.getP2Population() == b.getP2Population()){
            ans = 0;
        }
        return ans;
    }

    public ArrayList<IVisiblePlanet> quickSort(ArrayList<IVisiblePlanet> list){ //SORTING using ONLY MY POPULATION (player 1) ON PLANET
        if (list.size() <= 1){
            return list; //already sorted
        }
        ArrayList<IVisiblePlanet> sorted = new ArrayList<IVisiblePlanet>();
        ArrayList<IVisiblePlanet> lesser = new ArrayList<>();
        ArrayList<IVisiblePlanet> greater = new ArrayList<>();
        IVisiblePlanet pivot = list.get(list.size()-1);
        for (int i = 0; i < list.size()-1; i++)
        {
            if (compareTo(list.get(i),pivot) > 0) //Want the highest pop planet first, descending order
                lesser.add(list.get(i));
            else
                greater.add(list.get(i));
        }

        lesser = quickSort(lesser);
        greater = quickSort(greater);

        lesser.add(pivot);
        lesser.addAll(greater);
        sorted = lesser;

        return sorted;
    }
    public ArrayList<IVisiblePlanet> quickSortPlayerTwo(ArrayList<IVisiblePlanet> list){ //SORTING using ONLY MY POPULATION (player 1) ON PLANET
        if (list.size() <= 1){
            return list; //already sorted
        }
        ArrayList<IVisiblePlanet> sorted = new ArrayList<IVisiblePlanet>();
        ArrayList<IVisiblePlanet> lesser = new ArrayList<>();
        ArrayList<IVisiblePlanet> greater = new ArrayList<>();
        IVisiblePlanet pivot = list.get(list.size()-1);
        for (int i = 0; i < list.size()-1; i++)
        {
            if (compareToPlayerTwo(list.get(i),pivot) > 0) //Want the highest pop planet first, descending order
                lesser.add(list.get(i));
            else
                greater.add(list.get(i));
        }

        lesser = quickSort(lesser);
        greater = quickSort(greater);

        lesser.add(pivot);
        lesser.addAll(greater);
        sorted = lesser;

        return sorted;
    }

    public IVisiblePlanet getStrongestPlanet(Set<IVisiblePlanet> planets, int shuttles_needed){
        ArrayList<IVisiblePlanet> list = new ArrayList<>(planets);
        //We'll remove any neighboring planets that aren't ours
        for(int i = 0; i < list.size(); i++) {
            if(list.get(i).getOwner() != Owner.SELF) {
                list.remove(i);
                i--;
            }
        }
        //Now we'll check which population we are, one or two
        boolean isPlayerOne;
        if(list.size() == 0) {
            //No friendly neighbors at all
            return null;
        }
        IVisiblePlanet planetCheck = list.get(0);
        if(planetCheck.getP1Population() > planetCheck.getP2Population()) {
            isPlayerOne = true;
        } else {
            isPlayerOne = false;
        }
        //Sorting the list
        ArrayList<IVisiblePlanet> sortedNeighbors;
        if(isPlayerOne) {
            sortedNeighbors = quickSort(list);
        } else {
            sortedNeighbors = quickSortPlayerTwo(list);
        }
        IVisiblePlanet strongestPlanet = null;
        Iterator<IVisiblePlanet> iter = sortedNeighbors.iterator();
        while (iter.hasNext()){
            IVisiblePlanet currPlanet = iter.next();
            if(currPlanet != null) {
                if (futureStateChecker(currPlanet, 5)[1] > shuttles_needed && futureStateChecker(currPlanet, 5)[0] >= 0) {
                    strongestPlanet = currPlanet;
                }
            }
        }
        return strongestPlanet;
    }

    public int[] futureStateChecker(IVisiblePlanet planet, int futureTurn) {

        //Scoring Mechanism for Future Status of all Planets
        //Scoring: (From best to worst)
        /*
        3 = UNOCCUPIED planet or a planet that stays neutral. returns {3, 0} or {0, 0} if it stays neutral.
        2 = status of the planet changes fully in our favor (enemy planet --> my planet). returns {2, my current population - current enemy population - 1}
            Or, we are capped at the max for a planet that we own.
        1 = status of the planet changes slightly in our favor (enemy planet --> neutral planet). returns {1, 0}.
        0 = status of the planet does NOT change, 0. returns {0, my current population - current enemy population -1}
            or {0, current enemy population - my current population + 1}
        -1 = status of the planet changes slightly NOT  in our favor (myPlanet --> neutral). returns {-1, 0}.
        -2 = status of the planet changes fully NOT in our favor, deficit. (myPlanet --> enemy). returns {-2, (the current enemy population - our current population)}
        -10 = neutral or enemy planet at max. Cannot do anything about this planet. returns {-10, 0}
         */

        List<IShuttle> shuttles = planet.getIncomingShuttles();

        if(shuttles.isEmpty()) {
            if(planet.getTotalPopulation() == 0) {
                //Found an unoccupied planet.
                return new int[]{3, 0};
            }
        }
        Owner planetOwner = planet.getOwner();
        List<IShuttle> sortedShuttlesList = new ArrayList<>();

        //Now, sort the shuttles list.
        // Firstly, we will use a stack sorting method to get every shuttle in order (by their turns to arrival)
        //(This section of code uses a stack sorting algorithm)
        //Afterwards, we will make sure that the friendly shuttles appear in the sorted shuttles list before unfriendly ones.
        Stack<IShuttle> sortedShuttleStack = new Stack<>();
        Stack<IShuttle> helperStack = new Stack<>();
        for(IShuttle shuttle : shuttles) {
            helperStack.push(shuttle);
        }
        while(!helperStack.isEmpty()) {
            IShuttle poppedShuttle = helperStack.pop();
            int turnsToArrivalStack = poppedShuttle.getTurnsToArrival();

            while(!sortedShuttleStack.isEmpty() && sortedShuttleStack.peek().getTurnsToArrival() > turnsToArrivalStack) {
                helperStack.push(sortedShuttleStack.pop());
            }
            sortedShuttleStack.push(poppedShuttle);
        }
        //The shuttle list should now be in sorted order. (sortedShuttleStack has sorted shuttles, helperStack is empty)
        //Top of the sortedShuttleStack is the farthest shuttle (highest number of turns away.)
        //We will now make sure the friendly shuttles are before the enemy shuttles in the helperStack.
        while(!sortedShuttleStack.isEmpty()) {
            //count keeps track of how many friendly shuttles should be before the unfriendly shuttle we just popped.
            int count = 0;
            IShuttle poppedShuttleTwo = sortedShuttleStack.pop();
            int turnsToArrivalStackTwo = poppedShuttleTwo.getTurnsToArrival();
            Owner poppedShuttleTwoOwner = poppedShuttleTwo.getOwner();

            while(!helperStack.isEmpty() && poppedShuttleTwoOwner!= planetOwner && turnsToArrivalStackTwo == helperStack.peek().getTurnsToArrival()) {
                count += 1;
                sortedShuttleStack.push(helperStack.pop());
            }
            helperStack.push(poppedShuttleTwo);
            for(int i = 0; i < count; i++) {
                if(!sortedShuttleStack.isEmpty()) {
                    helperStack.push(sortedShuttleStack.pop());
                }
            }
        }
        //We now put the properly organized helperStack shuttles into the sortedShuttlesList.
        while(!helperStack.isEmpty()) {
            sortedShuttlesList.add(helperStack.pop());
        }

        long myPopulation;
        long enemyPopulation;
        if(planet.getP1Population() > planet.getP2Population()) {
            if (planet.getOwner() == Owner.SELF) {
                myPopulation = planet.getP1Population();
                enemyPopulation = planet.getP2Population();
            } else {
                //Belongs to opponent
                myPopulation = planet.getP2Population();
                enemyPopulation = planet.getP1Population();
            }
        } else if(planet.getP1Population() < planet.getP2Population()) {
            if (planet.getOwner() == Owner.SELF) {
                myPopulation = planet.getP2Population();
                enemyPopulation = planet.getP1Population();
            } else {
                myPopulation = planet.getP1Population();
                enemyPopulation = planet.getP2Population();
            }
        } else {
            //Planet is neutral
            //It's impossible to tell right now which population is ours.
            //So, we'll set p1 population as ours and p2 population as the enemy's as a default
            //The only prediction we can see for neutral planets is if it'll still be neutral, not which side it'll change to in 5 turns
            //However, this is ok because we'll be attacking neutral planets anyway
            //And neutral planets will not have a lot of spare population to use for defending as well.
            myPopulation = planet.getP1Population();
            enemyPopulation = planet.getP2Population();
        }
        long currMyPopulation = myPopulation;
        long currEnemyPopulation = enemyPopulation;
        long calculatedPop = planet.getTotalPopulation();
        long max = planet.getSize();
        int habitability = planet.getHabitability();

        int turnCount = 0; //how many steps we are going to be taking into the future

        for(IShuttle shuttle : sortedShuttlesList) {
            int turnsToArrival = shuttle.getTurnsToArrival();
            if(turnsToArrival > futureTurn) {
                //Shuttle won't be arriving within the number of turns. Don't need to care about it.
                break;
            }
            int loops = turnsToArrival - turnCount;

            for(int i = 0; i < loops; i++) {
                if(calculatedPop > max) {
                    if(myPopulation > enemyPopulation) {
                        //We would like to send out people so we're not capped at the max
                        return new int[]{2, (int) (currMyPopulation - currEnemyPopulation)};
                    } else {
                        //Can't do anything with this planet (either at max neutral, or max with enemy)
                        return new int[]{-10,0};
                    }
                }
                //This following section of code doing the population growth is from Planet.java:
                //First, we simulate the population growth with each turn
                double populationScaleFactor = 1. + (habitability / 100.);
                long popIncrease = (long) Math.min(max, Math.ceil(calculatedPop * populationScaleFactor));
                popIncrease -= calculatedPop; // determines how much total population will increase

                // split population increase proportionally between pop1 and pop2
                long p1fraction = (long) Math.floor(popIncrease * (myPopulation / (double) calculatedPop));
                long p2fraction = (long) Math.floor(popIncrease * (enemyPopulation / (double) calculatedPop));

                calculatedPop += popIncrease;
                myPopulation += p1fraction;
                enemyPopulation += p2fraction;
                //End code from Planet.java
                turnCount+=1;
            }
            calculatedPop += shuttle.getNumberPeople();
            //Add shuttle population
            if(shuttle.getOwner() == Owner.OPPONENT) {
                enemyPopulation += shuttle.getNumberPeople();
            } else {
                myPopulation += shuttle.getNumberPeople();
            }
        }

        if(planetOwner == Owner.SELF) { //Originally my planet
            if(myPopulation < enemyPopulation) {
                //Goes from my planet --> enemy planet (deficit)
                return new int[] {-2, (int) (currEnemyPopulation - currMyPopulation)};
            } else if(myPopulation > enemyPopulation) {
                //Stays the same (still my planet)
                return new int[] {0, (int) (currMyPopulation - currEnemyPopulation - 1)};
            } else {
                //Goes from my planet --> neutral planet
                return new int[] {-1, 0};
            }
        } else if(planetOwner == Owner.OPPONENT) { //Originally opponent's planet
            if(enemyPopulation < myPopulation) {
                //Goes from enemy planet --> my planet (spare population)
                return new int[] {2, (int) (currMyPopulation - currEnemyPopulation - 1)};
            } else if(enemyPopulation > myPopulation) {
                //Stays the same (still enemy)
                return new int[] {0, (int) (currMyPopulation - currEnemyPopulation + 1)};
            } else {
                //Goes from enemy planet --> neutral planet
                return new int[] {1, 0};
            }
        }
        else {
            //Originally neutral
            if(myPopulation == enemyPopulation) {
                //Stays the same (Still neutral)
                return new int[] {3, 0};
            }
        }
        //Just in case
        return new int[] {0, 0};
    }


    @Override
    public String getName() {
        return "5 Steps Ahead";
    }

    @Override
    public boolean compete() {
        return true;
    }
}