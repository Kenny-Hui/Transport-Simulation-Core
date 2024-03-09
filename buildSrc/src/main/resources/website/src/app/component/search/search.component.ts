import {Component, Input, OnInit} from "@angular/core";
import {AsyncPipe, NgForOf, NgIf} from "@angular/common";
import {MatAutocomplete, MatAutocompleteTrigger, MatOption} from "@angular/material/autocomplete";
import {MatDivider} from "@angular/material/divider";
import {MatFormField, MatLabel} from "@angular/material/form-field";
import {MatInput} from "@angular/material/input";
import {FormControl, ReactiveFormsModule} from "@angular/forms";
import {map, Observable} from "rxjs";
import {DataService} from "../../service/data.service";
import {SimplifyStationsPipe} from "../../pipe/simplifyStationsPipe";
import {SimplifyRoutesPipe} from "../../pipe/simplifyRoutesPipe";

@Component({
	selector: "app-search",
	standalone: true,
	imports: [
		AsyncPipe,
		MatAutocomplete,
		MatDivider,
		MatOption,
		NgForOf,
		NgIf,
		MatAutocompleteTrigger,
		MatFormField,
		MatInput,
		MatLabel,
		ReactiveFormsModule,
	],
	templateUrl: "./search.component.html",
	styleUrl: "./search.component.css"
})
export class SearchComponent implements OnInit {
	@Input() label!: string;
	@Input() includeRoutes!: boolean;
	searchBox = new FormControl("");
	searchedStations = new Observable<{ color: string, name: string }[]>();
	searchedRoutes = new Observable<{ color: string, name: string }[]>();
	hasStations = false;
	hasRoutes = false;

	constructor(private readonly dataService: DataService, private readonly simplifyStationsPipe: SimplifyStationsPipe, private readonly simplifyRoutesPipe: SimplifyRoutesPipe) {
	}

	ngOnInit() {
		const filter = (getList: () => { color: string, name: string }[], setHasData: (value: boolean) => void): Observable<{ color: string, name: string }[]> => this.searchBox.valueChanges.pipe(map(value => {
			if (value == null || value === "") {
				return [];
			} else {
				const matches: { color: string, name: string, index: number }[] = [];
				getList().forEach(({color, name}) => {
					const index = name.toLowerCase().indexOf(value.toLowerCase());
					if (index >= 0) {
						matches.push({color, name, index});
					}
				});
				const result: { color: string, name: string }[] = matches.sort((match1, match2) => {
					const indexDifference = match1.index - match2.index;
					return indexDifference === 0 ? match1.name.localeCompare(match2.name) : indexDifference;
				}).map(({color, name}) => ({color, name}));
				setHasData(result.length > 0);
				return result;
			}
		}));

		this.searchedStations = filter(() => this.simplifyStationsPipe.transform(this.dataService.getAllStations()), value => this.hasStations = value);
		this.searchedRoutes = filter(() => this.includeRoutes ? this.simplifyRoutesPipe.transform(this.dataService.getAllRoutes()) : [], value => this.hasRoutes = value);
	}
}
