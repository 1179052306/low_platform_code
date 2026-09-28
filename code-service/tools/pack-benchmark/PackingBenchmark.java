import java.math.BigDecimal;
import java.util.*;

public class PackingBenchmark {
    static class Row {
        String itemKey;
        BigDecimal volume;
        Row(String itemKey, BigDecimal volume){this.itemKey=itemKey;this.volume=volume;}
    }

    public static List<List<Row>> pack(List<Row> flatRows, BigDecimal containerFloat) {
        List<List<Row>> result = new ArrayList<>();
        if (flatRows==null || flatRows.isEmpty()) return result;
        final BigDecimal OPS_CONTAINER_CAPACITY = new BigDecimal("1000000");
        final BigDecimal allowed = OPS_CONTAINER_CAPACITY.add(containerFloat==null?new BigDecimal("50"):containerFloat);

        Map<String, List<Row>> grouped = new HashMap<>();
        for (Row r: flatRows) grouped.computeIfAbsent(r.itemKey==null?"":r.itemKey,k->new ArrayList<>()).add(r);
        List<Map.Entry<String,List<Row>>> entries = new ArrayList<>(grouped.entrySet());
        entries.sort((a,b)->{
            BigDecimal va=a.getValue().stream().map(r->r.volume).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal vb=b.getValue().stream().map(r->r.volume).reduce(BigDecimal.ZERO,BigDecimal::add);
            return vb.compareTo(va);
        });

        class Container{List<Row> rows=new ArrayList<>(); BigDecimal remaining=allowed; Set<String> items=new HashSet<>();}
        List<Container> containers=new ArrayList<>();
        NavigableMap<BigDecimal, Deque<Integer>> remainingMap=new TreeMap<>();
        Map<String,List<Integer>> itemToContainers=new HashMap<>();

        java.util.function.BiConsumer<BigDecimal,Integer> addToRem=(rem,idx)-> remainingMap.computeIfAbsent(rem,k->new ArrayDeque<>()).addLast(idx);
        java.util.function.BiConsumer<BigDecimal,Integer> removeFromRem=(rem,idx)->{
            Deque<Integer> dq=remainingMap.get(rem);
            if(dq!=null){dq.removeFirstOccurrence(idx); if(dq.isEmpty()) remainingMap.remove(rem);} };

        for(Map.Entry<String,List<Row>> entry: entries){
            String itemKey=entry.getKey();
            List<Row> rows=entry.getValue();
            rows.sort((r1,r2)->r2.volume.compareTo(r1.volume));
            for(Row row: rows){
                BigDecimal vol=row.volume; Integer chosenIdx=null; List<Integer> cand=itemToContainers.get(itemKey);
                if(cand!=null && !cand.isEmpty()){ BigDecimal bestRem=null; for(Integer idx:cand){ Container c=containers.get(idx); if(c.remaining.compareTo(vol)>=0){ BigDecimal remAfter=c.remaining.subtract(vol); if(bestRem==null|| remAfter.compareTo(bestRem)<0){bestRem=remAfter; chosenIdx=idx;} } } }
                if(chosenIdx==null){ Map.Entry<BigDecimal,Deque<Integer>> ceiling=remainingMap.ceilingEntry(vol); while(ceiling!=null){ Deque<Integer> dq=ceiling.getValue(); while(!dq.isEmpty() && containers.get(dq.peek()).remaining.compareTo(vol)<0) dq.pollFirst(); if(dq.isEmpty()){ remainingMap.remove(ceiling.getKey()); ceiling=remainingMap.ceilingEntry(vol); continue;} chosenIdx=dq.peekFirst(); break; } }
                if(chosenIdx!=null){ Container c=containers.get(chosenIdx); BigDecimal oldRem=c.remaining; removeFromRem.accept(oldRem,chosenIdx); c.rows.add(row); c.items.add(itemKey); c.remaining=c.remaining.subtract(vol); addToRem.accept(c.remaining,chosenIdx); itemToContainers.computeIfAbsent(itemKey,k->new ArrayList<>()).add(chosenIdx);
                } else { Container c=new Container(); c.rows.add(row); c.items.add(itemKey); c.remaining=allowed.subtract(vol); int idx=containers.size(); containers.add(c); addToRem.accept(c.remaining,idx); itemToContainers.computeIfAbsent(itemKey,k->new ArrayList<>()).add(idx);} }
        }
        for(Container c: containers) result.add(c.rows);
        return result;
    }

    public static void main(String[] args) {
        final int ROWS = 10_000;
        final int SKUS = 1_000;
        Random rnd = new Random(12345);
        List<Row> flat = new ArrayList<>(ROWS);
        for(int i=0;i<ROWS;i++){
            String key = "ITEM_" + (rnd.nextInt(SKUS));
            int w = 10 + rnd.nextInt(91);
            int h = 10 + rnd.nextInt(91);
            int l = 10 + rnd.nextInt(91);
            BigDecimal vol = new BigDecimal(w).multiply(new BigDecimal(h)).multiply(new BigDecimal(l));
            flat.add(new Row(key, vol));
        }
        BigDecimal floatUp = new BigDecimal("50");
        long t0 = System.nanoTime();
        List<List<Row>> containers = pack(flat, floatUp);
        long t1 = System.nanoTime();
        long ms = (t1-t0)/1_000_000;
        System.out.println("Rows="+ROWS+", SKUs="+SKUS+", Containers="+containers.size()+", Time(ms)="+ms);
    }
}
