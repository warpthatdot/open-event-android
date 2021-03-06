package org.fossasia.openevent.general.ticket

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.Navigation.findNavController
import kotlinx.android.synthetic.main.fragment_tickets.view.eventName
import kotlinx.android.synthetic.main.fragment_tickets.view.organizerName
import kotlinx.android.synthetic.main.fragment_tickets.view.progressBarTicket
import kotlinx.android.synthetic.main.fragment_tickets.view.register
import kotlinx.android.synthetic.main.fragment_tickets.view.ticketInfoTextView
import kotlinx.android.synthetic.main.fragment_tickets.view.ticketTableHeader
import kotlinx.android.synthetic.main.fragment_tickets.view.ticketsRecycler
import kotlinx.android.synthetic.main.fragment_tickets.view.time
import org.fossasia.openevent.general.AuthActivity
import org.fossasia.openevent.general.R
import org.fossasia.openevent.general.event.Event
import org.fossasia.openevent.general.event.EventUtils
import org.fossasia.openevent.general.utils.extensions.nonNull
import org.fossasia.openevent.general.utils.nullToEmpty
import org.koin.androidx.viewmodel.ext.android.viewModel

const val EVENT_ID: String = "EVENT_ID"
const val CURRENCY: String = "CURRENCY"
const val TICKET_ID_AND_QTY: String = "TICKET_ID_AND_QTY"
const val REDIRECTED_FROM_TICKETS = "REDIRECTED_FROM_TICKETS"

class TicketsFragment : Fragment() {
    private val ticketsRecyclerAdapter: TicketsRecyclerAdapter = TicketsRecyclerAdapter()
    private val ticketsViewModel by viewModel<TicketsViewModel>()
    private var id: Long = -1
    private var currency: String? = null
    private lateinit var rootView: View
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var ticketIdAndQty = ArrayList<Pair<Int, Int>>()
    private val AUTH_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bundle = this.arguments
        if (bundle != null) {
            id = bundle.getLong(EVENT_ID, -1)
            currency = bundle.getString(CURRENCY, null)
        }
        ticketsRecyclerAdapter.setCurrency(currency)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_tickets, container, false)
        val activity = activity as? AppCompatActivity
        activity?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity?.supportActionBar?.title = "Ticket Details"
        setHasOptionsMenu(true)

        val ticketSelectedListener = object : TicketSelectedListener {
            override fun onSelected(ticketId: Int, quantity: Int) {
                handleTicketSelect(ticketId, quantity)
            }
        }
        ticketsRecyclerAdapter.setSelectListener(ticketSelectedListener)
        rootView.ticketsRecycler.layoutManager = LinearLayoutManager(activity)

        rootView.ticketsRecycler.adapter = ticketsRecyclerAdapter
        rootView.ticketsRecycler.isNestedScrollingEnabled = false

        linearLayoutManager = LinearLayoutManager(context)
        linearLayoutManager.orientation = RecyclerView.VERTICAL
        rootView.ticketsRecycler.layoutManager = linearLayoutManager

        ticketsViewModel.error
            .nonNull()
            .observe(this, Observer {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            })

        ticketsViewModel.progressTickets
            .nonNull()
            .observe(this, Observer {
                rootView.progressBarTicket.isVisible = it
                rootView.ticketTableHeader.isGone = it
                rootView.register.isGone = it
            })

        ticketsViewModel.event
            .nonNull()
            .observe(this, Observer {
                loadEventDetails(it)
            })

        ticketsViewModel.ticketTableVisibility
            .nonNull()
            .observe(this, Observer { ticketTableVisible ->
                rootView.ticketTableHeader.isVisible = ticketTableVisible
                rootView.register.isVisible = ticketTableVisible
                rootView.ticketsRecycler.isVisible = ticketTableVisible
                rootView.ticketInfoTextView.isGone = ticketTableVisible
            })

        ticketsViewModel.loadEvent(id)
        ticketsViewModel.loadTickets(id)

        ticketsViewModel.tickets
            .nonNull()
            .observe(this, Observer {
                ticketsRecyclerAdapter.addAll(it)
                ticketsRecyclerAdapter.notifyDataSetChanged()
            })

        rootView.register.setOnClickListener {
            if (!ticketsViewModel.totalTicketsEmpty(ticketIdAndQty)) {
                checkForAuthentication()
            } else {
                handleNoTicketsSelected()
            }
        }

        return rootView
    }

    private fun checkForAuthentication() {
        if (ticketsViewModel.isLoggedIn())
            redirectToAttendee()
        else {
            Toast.makeText(context, "You need to log in first!", Toast.LENGTH_LONG).show()
            redirectToLogin()
        }
    }

    private fun redirectToAttendee() {
        val bundle = Bundle()
        bundle.putLong(EVENT_ID, id)
        bundle.putSerializable(TICKET_ID_AND_QTY, ticketIdAndQty)
        findNavController(rootView).navigate(R.id.attendeeFragment, bundle)
    }

    private fun redirectToLogin() {
        val intent = Intent(activity, AuthActivity::class.java)
        val bundle = Bundle()
        bundle.putBoolean(REDIRECTED_FROM_TICKETS, true)
        intent.putExtras(bundle)
        startActivityForResult(intent, AUTH_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == AUTH_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK)
                redirectToAttendee()
            else
                Toast.makeText(context, "Sign in failed!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleTicketSelect(id: Int, quantity: Int) {
        val pos = ticketIdAndQty.map { it.first }.indexOf(id)
        if (pos == -1) {
            ticketIdAndQty.add(Pair(id, quantity))
        } else {
            ticketIdAndQty[pos] = Pair(id, quantity)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                activity?.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadEventDetails(event: Event) {
        rootView.eventName.text = event.name
        rootView.organizerName.text = "by ${event.organizerName.nullToEmpty()}"
        val startsAt = EventUtils.getLocalizedDateTime(event.startsAt)
        val endsAt = EventUtils.getLocalizedDateTime(event.endsAt)
        rootView.time.text = EventUtils.getFormattedDateTimeRangeDetailed(startsAt, endsAt)
    }

    private fun handleNoTicketsSelected() {
        val builder = AlertDialog.Builder(activity)
        builder.setMessage(resources.getString(R.string.no_tickets_message))
                .setTitle(resources.getString(R.string.whoops))
                .setPositiveButton(resources.getString(R.string.ok)) { dialog, _ -> dialog.cancel() }
        val alert = builder.create()
        alert.show()
    }
}
