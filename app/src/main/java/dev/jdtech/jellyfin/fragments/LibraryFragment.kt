package dev.jdtech.jellyfin.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.viewmodels.LibraryViewModel
import dev.jdtech.jellyfin.adapters.ViewItemPagingAdapter
import dev.jdtech.jellyfin.databinding.FragmentLibraryBinding
import dev.jdtech.jellyfin.dialogs.ErrorDialogFragment
import dev.jdtech.jellyfin.dialogs.SortDialogFragment
import dev.jdtech.jellyfin.utils.SortBy
import dev.jdtech.jellyfin.utils.checkIfLoginRequired
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.SortOrder
import java.lang.IllegalArgumentException
import javax.inject.Inject

@AndroidEntryPoint
class LibraryFragment : Fragment() {

    private lateinit var binding: FragmentLibraryBinding
    private val viewModel: LibraryViewModel by viewModels()
    private val args: LibraryFragmentArgs by navArgs()

    private lateinit var errorDialog: ErrorDialogFragment

    @Inject
    lateinit var sp: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.library_menu, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_sort_by -> {
                            SortDialogFragment(
                                args.libraryId,
                                args.libraryType,
                                viewModel,
                                "sortBy"
                            ).show(
                                parentFragmentManager,
                                "sortdialog"
                            )
                            true
                        }
                        R.id.action_sort_order -> {
                            SortDialogFragment(
                                args.libraryId,
                                args.libraryType,
                                viewModel,
                                "sortOrder"
                            ).show(
                                parentFragmentManager,
                                "sortdialog"
                            )
                            true
                        }
                        else -> false
                    }
                }
            }, viewLifecycleOwner, Lifecycle.State.RESUMED
        )

        binding.errorLayout.errorRetryButton.setOnClickListener {
            viewModel.loadItems(args.libraryId, args.libraryType)
        }

        binding.errorLayout.errorDetailsButton.setOnClickListener {
            errorDialog.show(
                parentFragmentManager,
                "errordialog"
            )
        }

        binding.itemsRecyclerView.adapter =
            ViewItemPagingAdapter(ViewItemPagingAdapter.OnClickListener { item ->
                navigateToMediaInfoFragment(item)
            })

        (binding.itemsRecyclerView.adapter as ViewItemPagingAdapter).addLoadStateListener {
            when (it.refresh) {
                is LoadState.Error -> {
                    val error = Exception((it.refresh as LoadState.Error).error)
                    bindUiStateError(LibraryViewModel.UiState.Error(error))
                }
                is LoadState.Loading -> {
                    bindUiStateLoading()
                }
                is LoadState.NotLoading -> {
                    binding.loadingIndicator.isVisible = false
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onUiState(viewLifecycleOwner.lifecycleScope) { uiState ->
                    when (uiState) {
                        is LibraryViewModel.UiState.Normal -> bindUiStateNormal(uiState)
                        is LibraryViewModel.UiState.Loading -> bindUiStateLoading()
                        is LibraryViewModel.UiState.Error -> bindUiStateError(uiState)
                    }
                }

                // Sorting options
                val sortBy = SortBy.fromString(sp.getString("sortBy", SortBy.defaultValue.name)!!)
                val sortOrder = try {
                    SortOrder.valueOf(sp.getString("sortOrder", SortOrder.ASCENDING.name)!!)
                } catch (e: IllegalArgumentException) {
                    SortOrder.ASCENDING
                }

                viewModel.loadItems(
                    args.libraryId,
                    args.libraryType,
                    sortBy = sortBy,
                    sortOrder = sortOrder
                )
            }
        }
    }

    private fun bindUiStateNormal(uiState: LibraryViewModel.UiState.Normal) {
        val adapter = binding.itemsRecyclerView.adapter as ViewItemPagingAdapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                uiState.items.collect {
                    adapter.submitData(it)
                }
            }
        }
        binding.loadingIndicator.isVisible = false
        binding.itemsRecyclerView.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateLoading() {
        binding.loadingIndicator.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateError(uiState: LibraryViewModel.UiState.Error) {
        errorDialog = ErrorDialogFragment(uiState.error)
        binding.loadingIndicator.isVisible = false
        binding.itemsRecyclerView.isVisible = false
        binding.errorLayout.errorPanel.isVisible = true
        checkIfLoginRequired(uiState.error.message)
    }

    private fun navigateToMediaInfoFragment(item: BaseItemDto) {
        findNavController().navigate(
            LibraryFragmentDirections.actionLibraryFragmentToMediaInfoFragment(
                item.id,
                item.name,
                item.type
            )
        )
    }
}