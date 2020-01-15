package nachos.threads;

import java.util.Comparator;

public class PriorityQueueDSComparator implements Comparator<PriorityQueueDS> {

	@Override
	public int compare(PriorityQueueDS arg0, PriorityQueueDS arg1) {
		// TODO Auto-generated method stub
		if (arg0.getWaitTime() <= arg1.getWaitTime()) {
			return 1;
		}
		else
			return 0;
	}
}