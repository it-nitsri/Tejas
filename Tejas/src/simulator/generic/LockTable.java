package generic;

import java.util.Hashtable;
import java.util.ArrayList;

//This class is absolutely needed
//this avoids the timed wait BS
//This is beacuse if we have not seen an unlock enter
//we donot know if the lock has been acquired or not!

public class LockTable {
	public static Hashtable<Long,Integer>            acquired_locks = new Hashtable<Long,Integer>();
	public static Hashtable<Long,ArrayList<Integer>> waiting_locks = new Hashtable<Long,ArrayList<Integer>>();

	public static Object locking_mutex = new Object();

	private static boolean debug = false;
	//returns true if successful
	public static boolean try_locking(int thread, long addr){

		boolean ret = false;
		//if lock is not present
		//acquire it
		//else wait for it
		synchronized(locking_mutex) {

			if(acquired_locks.contains(addr)) {
				//locked by some thread
				//wait for it!

				if(waiting_locks.contains(addr)) {
					waiting_locks.get(addr).add(thread);
				} else{
					ArrayList<Integer> waiting_list = new ArrayList<Integer>();
					waiting_list.add(thread);
					waiting_locks.put(addr,waiting_list);
				}
				ret = false;
			}

			else {
				//can acquire so acquire!
				acquired_locks.put(addr,thread);
				ret = true;
			}
		}

		return ret;
	}

	//This function returns
	//the thread that must be woken up
	public static int try_unlocking(int thread, long addr){


		//unlock for a lock must come from the same thread that locked
		//the lock
		int ret = -1;
		
		synchronized(locking_mutex) {
			
			if(acquired_locks.containsKey(addr) == false) {
				//System.out.println(acquired_locks);
				if(debug) System.out.println("Thread "+thread+" :: ["+addr+ "] ::Tried unlocking an unlocked lock");
				return -1;
			}

			if(acquired_locks.get(addr).equals(thread) == false) {
				//It is in the spec that we can unlock a lock from any thread
				//misc.Error.showErrorAndExit("Tried unlocking from a different thread");
			}

			if(waiting_locks.contains(addr)) {
				//someone is waiting for this lock
				ret = waiting_locks.get(addr).remove(0).intValue();
				if(waiting_locks.get(addr).size() ==0)
					waiting_locks.remove(addr);
				acquired_locks.put(addr,ret);
			}

			else {
				//no one is waiting for this
				//make sure acquired doesnt say this is a problem
				acquired_locks.remove(addr);
			}
			
		}

		return ret;
	}
}
