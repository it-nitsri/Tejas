/*****************************************************************************
				Tejas Simulator
------------------------------------------------------------------------------------------------------------

   Copyright [2010] [Indian Institute of Technology, Delhi]
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
------------------------------------------------------------------------------------------------------------

	Contributors:  Shubhankar S. Singh

Reference: A case for (partially) TAgged GEometric history length branch prediction
Authors: Andre Seznec and Pierre Michaud
Source Code (modified from): http://www.irisa.fr/caps/	
 *****************************************************************************/

package pipeline.branchpredictor;
import pipeline.ExecutionEngine;
import java.util.*;
import java.lang.Math;

public class TAGE extends BranchPredictor {
	public static int LOGB; // Size of Bimodal predictor
	public static int LOGG;
	public static int NHIST = 7; // Number of Tagged tables
	public static int CBITS = 3;
	public static int TBITS = 12;
	public static int MAXHIST = 131; // Max size of history
	public static int MINHIST = 5; // Min size of history

	class folded_history
	{
		int comp;
		int CLENGTH;
		int OLENGTH;
		int OUTPOINT;

		folded_history (int original_length, int compressed_length)
		{
			comp = 0;
			OLENGTH = original_length;
			CLENGTH = compressed_length;
			OUTPOINT = OLENGTH % CLENGTH;
		}

		void update (int[] h)
		{
			// ASSERT ((comp >> CLENGTH) == 0);
			comp = (comp << 1) | h[0];
			comp ^= h[OLENGTH] << OUTPOINT;
			comp ^= (comp >> CLENGTH);
			comp &= (1 << CLENGTH) - 1;
		}
	}


	class PREDICTOR
	{
		// bimodal table entry
		class bentry
		{
			int hyst;
			int pred;
			bentry ()
			{
				pred = 0;
				hyst = 1;
			}
		}


		class gentry
		{
			int ctr;
			int tag;
			int ubit;
			gentry ()
			{
				ctr = 0;
				tag = 0;
				ubit = 0;
			}
		}

		class otable
		{
			gentry[] gentrytable;
			otable (int size)
			{
				gentrytable = new gentry[size];
				for(int i=0; i<size; i++){
					gentrytable[i] = new gentry();
				}
			}
			gentry geti (int i)
			{
				return gentrytable[i];
			}
			void settag(int i, int tag)
			{
				gentrytable[i].tag = tag;
			}
			void setctr(int i, int ctr)
			{
				gentrytable[i].ctr = ctr;
			}
			void setubit(int i, int ubit)
			{
				gentrytable[i].ubit = ubit;
			}
		}

		// predictor storage data
		int PWIN;
		// 4 bits to determine whether newly allocated entries should be considered as
		// valid or not for delivering  the prediction
		int TICK;
		int phist;
		// use a path history as for the OGEHL predictor
		int ghist[] = new int[MAXHIST];
		folded_history ch_i[] = new folded_history[NHIST];
		folded_history ch_t0[] = new folded_history[NHIST];
		folded_history ch_t1[] = new folded_history[NHIST];
		bentry btable[];
		otable gtable[];

		// used for storing the history lengths
		int m[] = new int[NHIST];

		PREDICTOR (int PCBits)
		{
			LOGB = PCBits;	
			LOGG = LOGB-4;
			int STORAGESIZE = 0;
			// ghist = new int[MAXHIST];
			for(int i=0; i<MAXHIST; i++){
				ghist[i]=0;
			}
			// computes the geometric history lengths   
			m[0] = MAXHIST - 1;
			m[NHIST - 1] = MINHIST;
			for(int i = 1; i < NHIST - 1; i++)
			{
				double MINHISTd = MINHIST;
				double MAXHISTd = MAXHIST;
				double Id       = i;
				double NHISTd   = NHIST;
				double val = MINHISTd * Math.pow((MAXHISTd-1)/MINHISTd, Id/(NHISTd-1)) + 0.5;
				m[NHIST-1-i] = (int)val;
			}
			STORAGESIZE = 0;

			for(int i = NHIST - 1; i >= 0; i--)
			{
				ch_i[i] = new folded_history(m[i],LOGG);
				STORAGESIZE += (1 << LOGG) * (5 + TBITS - ((i + (NHIST & 1)) / 2));
			}
			STORAGESIZE += (1 << LOGB) + (1 << (LOGB - 2));

			for (int i = 0; i < NHIST; i++)
			{
				ch_t0[i] = new folded_history(ch_i[i].OLENGTH, TBITS - ((i + (NHIST & 1)) / 2));
				ch_t1[i] = new folded_history(ch_i[i].OLENGTH, TBITS - ((i + (NHIST & 1)) / 2) - 1);
			}

			btable = new bentry[1 << LOGB];
			for(int i=0; i<btable.length; i++){
				btable[i] = new bentry();
			}

			gtable = new otable[NHIST];
			for (int i = 0; i < NHIST; i++)
			{
				gtable[i] = new otable(1 << LOGG);
			}

		}
		int bindex (long pc)
		{
			return (int)(pc & ((1 << LOGB) - 1));
		}

		int GI[] = new int[NHIST];
		int BI;

		int F (int A, int size, int bank)
		{
			int A1, A2;
			A = A & ((1 << size) - 1);
			A1 = (A & ((1 << LOGG) - 1));
			A2 = (A >> LOGG);
			A2 = ((A2 << bank) & ((1 << LOGG) - 1)) + (A2 >> (LOGG - bank));
			A = A1 ^ A2;
			A = ((A << bank) & ((1 << LOGG) - 1)) + (A >> (LOGG - bank));
			return (A);
		}
		int gindex (long pc, int bank)
		{
			int index;
			if (m[bank] >= 16){
				index = (int) (pc ^ (pc >> ((LOGG - (NHIST - bank - 1)))) ^ ch_i[bank].comp ^ F (phist, 16, bank));
			}
			else{
				index = (int) (pc ^ (pc >> (LOGG - NHIST + bank + 1)) ^	ch_i[bank].comp ^ F (phist, m[bank], bank));
			}
			return (index & ((1 << (LOGG)) - 1));
		}

		//  tag computation
		int gtag (long pc, int bank)
		{
			int tag = (int) (pc ^ ch_t0[bank].comp ^ (ch_t1[bank].comp << 1));
			return (tag & ((1 << (TBITS - ((bank + (NHIST & 1)) / 2))) - 1));
			//does not use the same length for all the components
		}

		int ctrupdate (int ctr, boolean taken, int nbits)
		{
			int diff = 0;
			if (taken)
			{
				if (ctr < ((1 << (nbits - 1)) - 1))
				{
					diff=1;
				}
			}
			else
			{
				if (ctr > -(1 << (nbits - 1)))
				{
					diff=-1;
				}
			}
			return diff;
		}
		int altbank;

		// prediction given by longest matching global history
		// altpred contains the alternate prediction

		class rp_ret
		{
			int retbank;
			boolean retval;
			boolean altpred;
			rp_ret ()
			{
				retbank=NHIST;
				retval=false;
				altpred=false;
			}
		};	


		boolean getbim (long pc)
		{
			return (btable[BI].pred > 0);
		}

		rp_ret read_prediction (long pc)
		{
			rp_ret retv = new rp_ret(); 
			retv.retbank = NHIST;
			altbank = NHIST;

			for (int i = 0; i < NHIST; i++)
			{
				// System.out.println("# "+i+" "+gtable.length);
				// System.out.println("# "+i+" "+GI.length);
				// System.out.println("# "+i+" "+GI[i]);
				// int tagl = gtable[i].geti(GI[i]).tag; 
				if (gtable[i].geti(GI[i]).tag == gtag (pc, i))
				{
					retv.retbank = i;
					break;
				}
			}
			for (int i = retv.retbank + 1; i < NHIST; i++)
			{
				if (gtable[i].geti(GI[i]).tag == gtag (pc, i))
				{
				  altbank = i;
				  break;
				}
			}
			if (retv.retbank < NHIST)
			{
				if (altbank < NHIST){
					retv.altpred = (gtable[altbank].geti(GI[altbank]).ctr >= 0);
				}
				else{
					retv.altpred = getbim (pc);
				}
				//if the entry is recognized as a newly allocated entry and 
				//counter PWIN is negative use the alternate prediction
				// see section 3.2.4
				if ((PWIN < 0) || (Math.abs (2 * gtable[retv.retbank].geti(GI[retv.retbank]).ctr + 1) != 1) || (gtable[retv.retbank].geti(GI[retv.retbank]).ubit != 0)){
					retv.retval = (gtable[retv.retbank].geti(GI[retv.retbank]).ctr >= 0);
					return (retv);
				}
				else{
					retv.retval = retv.altpred;
					return (retv);
				}

			}
			else
			{
				retv.altpred = getbim (pc);
				retv.retval = retv.altpred;
				return (retv);
			}
		}

		// PREDICTION
		rp_ret retv;
		boolean alttaken, pred_taken;
		int bank;
		boolean get_prediction (long pc)
		{
			// computes the table addresses
			for (int i = 0; i < NHIST; i++){
				GI[i] = gindex (pc, i);
			}
			BI = bindex (pc);

			retv = read_prediction (pc);
			// bank contains the number of the matching table, NHIST if no match
			// pred_taken is the prediction
			// alttaken is the alternate prediction
			bank = retv.retbank;
			alttaken = retv.altpred;
			pred_taken = retv.retval;
			
			return retv.retval;
		}

		// update  the bimodal predictor
		void baseupdate (long pc, boolean Taken)
		{
			//just a normal 2-bit counter apart that hysteresis is shared
			if (Taken == getbim (pc))
			{

				if (Taken)
				{
					if (btable[BI].pred>0){
						btable[BI >> 2].hyst = 1;
					}
				}
				else
				{
					if (btable[BI].pred==0){
						btable[BI >> 2].hyst = 0;
					}
				}
			}
			else
			{
				int inter = (btable[BI].pred << 1) + btable[BI >> 2].hyst;
				if (Taken)
				{
					if (inter < 3){
						inter += 1;
					}
				}
				else
				{
					if (inter > 0){
						inter--;
					}
				}

				btable[BI].pred = inter >> 1;
				btable[BI >> 2].hyst = (inter & 1);
			}
		}
		//just building our own simple pseudo random number generator based on linear feedback shift register
		int Seed;
		int MYRANDOM ()
		{
			Seed = ((1 << 2 * NHIST) + 1) * Seed + 0xf3f531;
			Seed = (Seed & ((1 << (2 * (NHIST))) - 1));
			return (Seed);
		};


		// PREDICTOR UPDATE
		void update_predictor (long pc, boolean taken)
		{

		    int NRAND = MYRANDOM ();

			// in a real processor, it is not necessary to re-read the predictor at update
			// it suffices to propagate the prediction along with the branch instruction
			boolean ALLOC = ((pred_taken != taken) & (bank > 0));


			if (bank < NHIST)
			{
				boolean loctaken = (gtable[bank].geti(GI[bank]).ctr >= 0);
				boolean PseudoNewAlloc = (Math.abs (2 * gtable[bank].geti(GI[bank]).ctr + 1) == 1) && (gtable[bank].geti(GI[bank]).ubit == 0);
				// is entry "pseudo-new allocated" 

				if (PseudoNewAlloc)
				{
					if (loctaken == taken){
						ALLOC = false;
					}
					// if the provider component  was delivering the correct prediction; no need to allocate a new entry
					//even if the overall prediction was false
					//see section 3.2.4
					if (loctaken != alttaken)
					{
						if (alttaken == taken)
						{
							if (PWIN < 7)
							PWIN++;
						}
						else if (PWIN > -8){
							PWIN--;
						}
					}
				}
			}



			// try to allocate a  new entries only if prediction was wrong
			if (ALLOC)
			{
				// is there some "unuseful" entry to allocate
				int min = 3;
				for (int i = 0; i < bank; i++)
				{
					if (gtable[i].geti(GI[i]).ubit < min){
						min = gtable[i].geti(GI[i]).ubit;
					}
				}

				if (min > 0)
				{
					//NO UNUSEFUL ENTRY TO ALLOCATE: age all possible targets, but do not allocate
					for (int i = bank - 1; i >= 0; i--)
					{
						int ubitl = gtable[i].geti(GI[i]).ubit;	
						gtable[i].setubit(GI[i], ubitl-1);
					}
				}
				else
				{
					//YES: allocate one entry, but apply some randomness
					// bank I is twice more probable than bank I-1     
					int Y = NRAND & ((1 << (bank - 1)) - 1);
					int X = bank - 1;
					while ((Y & 1) != 0)
					{
						X--;
						Y >>= 1;
					}
					for (int i = X; i >= 0; i--)
					{
						int T = i;
						if ((gtable[T].geti(GI[T]).ubit == min))
						{
							// gtable[T].geti(GI[T]).tag = gtag(pc, T);
							gtable[T].settag(GI[T], gtag(pc, T));
							// gtable[T].geti(GI[T]).ctr = (taken) ? 0 : -1;
							gtable[T].setctr(GI[T] , (taken) ? 0 : -1);
							// gtable[T].geti(GI[T]).ubit = 0;
							gtable[T].setubit(GI[T] , 0);
							break;
						}
					}
				}

			}


			//periodic reset of ubit: reset is not complete but bit by bit
			TICK++;
			if ((TICK & ((1 << 18) - 1)) == 0)
			{
				int X = (TICK >> 18) & 1;
				if ((X & 1) == 0){
					X = 2;
				}
				for (int i = 0; i < NHIST; i++){
					for (int j = 0; j < (1 << LOGG); j++){
						int ubitl = gtable[i].geti(j).ubit & X;
						gtable[i].setubit(j, ubitl);
					}
				}
			}

			// update the counter that provided the prediction, and only this counter
			if (bank < NHIST)
			{
				int diff = ctrupdate (gtable[bank].geti(GI[bank]).ctr, taken, CBITS);
				int ctrl = gtable[bank].geti(GI[bank]).ctr;
				gtable[bank].setctr(GI[bank], ctrl+diff);
			}
			else
			{
				baseupdate (pc, taken);
			}
			// update the ubit counter
			if ((pred_taken != alttaken))
			{
				if (pred_taken == taken)
				{
					if (gtable[bank].geti(GI[bank]).ubit < 3){
						int ubitl = gtable[bank].geti(GI[bank]).ubit + 1;
						gtable[bank].setubit(GI[bank], ubitl);	
					}
				}
				else
				{
					if (gtable[bank].geti(GI[bank]).ubit > 0){
						int ubitl = gtable[bank].geti(GI[bank]).ubit - 1;
						gtable[bank].setubit(GI[bank], ubitl);
					}
				}
			}

			// update global history and cyclic shift registers
			//use also history on unconditional branches as for OGEHL predictors.    

			// ghist = (ghist << 1);
			for(int i=MAXHIST-1; i>0; i--){
				ghist[i] = ghist[i-1];
			}
			if (taken){
				ghist[0] = 1;
			}
			else{
				ghist[0] = 0;
			}

			phist = (int) ((phist << 1) + (pc & 1));
			phist = (phist & ((1 << 16) - 1));
			for (int i = 0; i < NHIST; i++)
			{
				ch_i[i].update (ghist);
				ch_t0[i].update (ghist);
				ch_t1[i].update (ghist);
			}
		};

	}


	public static PREDICTOR PTAGE;
	public TAGE(ExecutionEngine containingExecEngine,int PCBits,int saturating_bits)
	{
		super(containingExecEngine);
		PTAGE = new PREDICTOR(PCBits);
	}

	public void Train(long address, boolean outcome, boolean predict) 
	{
		PTAGE.update_predictor(address,outcome);
	}

	public boolean predict(long address, boolean outcome) 
	{
		return PTAGE.get_prediction(address);
	}	

}
